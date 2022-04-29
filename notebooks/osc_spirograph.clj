;; # ꩜ An OSC _fourieristic_ Spirograph
;; This short notebook shows how to combine an [OSC](https://en.wikipedia.org/wiki/Open_Sound_Control)
;; controller with vector graphic visualizations in Clerk. OSC is generally used for networking live multimedia
;; devices and sound synthesizers, but as [noted a while ago by Joe Armstrong](https://joearms.github.io/published/2016-01-28-A-Badass-Way-To-Connect-Programs-Together.html) its
;; simplicity makes it an interesting choice for exchanging data between machines.

(ns osc-spirograph
  (:require [nextjournal.clerk :as clerk]
            [clojure.java.io :as io])
  (:import (com.illposed.osc ConsoleEchoServer OSCMessageListener OSCMessageEvent OSCMessage)
           (com.illposed.osc.messageselector JavaRegexAddressMessageSelector)
           (org.slf4j LoggerFactory)
           (java.net InetSocketAddress)
           (javax.imageio ImageIO)))

;; By means of [TouchOSC](https://hexler.net/touchosc) we're building a simple
;; touch interface looking like this

^{::clerk/visibility :hide}
(ImageIO/read (io/resource "spirograph.png"))

;; In order to receive OSC messages, we start by initializing an OSC Server instance. We're overlaying an extra broadcast (via clojure tap) of received OSC messages on top of the simple echo server provided by the [JavaOSC library](https://github.com/hoijui/JavaOSC). This is Clojure/Java interop at its best :-)
(when-not (System/getenv "NOSC")
  (defonce osc
    (doto (proxy [ConsoleEchoServer]
                 [(InetSocketAddress. "0.0.0.0" 6669)
                  (LoggerFactory/getLogger ConsoleEchoServer)]
            (start []
              (.. ^ConsoleEchoServer this
                  getDispatcher
                  (addListener (JavaRegexAddressMessageSelector. ".*")
                               (reify
                                 OSCMessageListener
                                 (^void acceptMessage [_this ^OSCMessageEvent event]
                                   (tap> (.getMessage event))))))
              (proxy-super start)))
      .start)))

;; The following viewer is used to sync models between clojure and the client.

^{::clerk/no-cache true
  ::clerk/viewer {:fetch-fn (fn [_ x] x)
                  :transform-fn (fn [{::clerk/keys [var-from-def]}] {:value @@var-from-def})
                  :render-fn '(fn [{:keys [value]}]
                                (defonce model (atom nil))
                                (-> (swap! model (partial merge-with (fn [old new] (if (vector? old) (mapv merge old new) new))) value)
                                    (update :phasors (partial mapv #(dissoc % :group)))
                                    (dissoc :drawing :curve)))}}

;; This is the model representing the coefficients of our spirograph. Three [phasors](https://en.wikipedia.org/wiki/Phasor) each with an ampliture and an angular frequency.
(def model
  (atom {:mode 0
         :phasors [{:amplitude 0.9 :frequency 0.2 :color "#f43f5e"}
                   {:amplitude 0.5 :frequency -0.35 :color "#65a30d"}
                   {:amplitude 0.125 :frequency 0.4 :color "#4338ca"}]}))

;; the linear faders in the above UX will control the amplitude, while the radial controllers
;; will control the frequencies

;; We configured our OSC message arguments to always be a vector of the form
;;
;;     [value & path]
;;
;; where value is changed by the controller we're interacting with, while the tail of the arguments is a valid path in the model above. In this example we're ignoring the OSC message address.
;;
(defn osc->map [^OSCMessage m]
  (let [[v & path] (map #(cond-> % (string? %) keyword) (.getArguments m))]
    {:value (if (= :phasors (first path)) (float (/ v 100)) v)
     :path path}))

;; a helper for updating our model and recomputing the notebook (without redoing all of Clerk static analysis).
(defn update-model! [f]
  (swap! model f)
  (binding [*ns* (find-ns 'osc-spirograph)]
    (clerk/recompute!)))

(defn osc-message-handler [osc-message]
  (let [{:keys [path value]} (osc->map osc-message)]
    (update-model! #(assoc-in % path value))))

;; Clerk won't cache forms returning nil values, hence the do here.
(defonce tapped
  (add-tap osc-message-handler))

^{::clerk/visibility :fold ::clerk/viewer :hide-result}
(def spirograph-viewer
  {:render-fn '(fn [_]
                 (v/html
                   [v/with-d3-require {:package "two.js@0.7.13"}
                    (fn [Two]
                      (reagent/with-let
                        [Vector (.-Vector Two) Line (.-Line Two) Group (.-Group Two)
                         world-matrix (.. Two -Utils -getComputedMatrix)
                         R 200 MAXV 1000 T (* js/Math.PI 0.05) time-scale 0.09
                         phasor-group (fn [drawing parent {:keys [amplitude color]}]
                                       (let [G (doto (Group.)
                                                 (j/assoc! :position
                                                           (j/get-in parent [:children 0 :vertices 1]
                                                                     (Vector. (/ (.-width drawing) 2)
                                                                              (/ (.-height drawing) 2)))))]
                                         (.add parent G)
                                         (.add G (doto (Line. 0.0 0.0 (* amplitude R) 0.0)
                                                   (j/assoc! :linewidth 7)
                                                   (j/assoc! :stroke color)
                                                   (j/assoc! :cap "round")
                                                   ))
                                         G))
                         build-phasors (fn [{:as m :keys [drawing]}]
                                        (update m :phasors
                                                #(:phasors (reduce
                                                            (fn [{:as acc :keys [parent-group]} params]
                                                              (let [g (phasor-group drawing parent-group params)]
                                                                (-> acc
                                                                    (update :phasors conj (assoc params :group g))
                                                                    (assoc :parent-group g))))
                                                            {:parent-group (.-scene drawing) :phasors []}
                                                            %))))
                         update-phasor! (fn [{:keys [amplitude frequency group]} dt]
                                         (when group
                                           (j/assoc-in! group [:children 0 :vertices 1 :x] (* amplitude R))
                                           (j/update! group :rotation + (* T frequency dt))))
                         build-curve (fn [{:as m :keys [drawing]}]
                                       (assoc m :curve
                                                (doto (.makeCurve drawing)
                                                  (j/assoc! :closed false)
                                                  (j/assoc! :stroke "#5b21b6")
                                                  (j/assoc! :linewidth 5)
                                                  (j/assoc! :opacity 0.8)
                                                  .noFill)))
                         pen-position (fn [{:keys [phasors]}]
                                        (let [{:keys [amplitude group]} (last phasors)]
                                          (.copy (Vector.)
                                                 (-> group world-matrix (.multiply (* amplitude R) 0.0 1.0)))))
                         ->color (fn [{:keys [phasors]}]
                                   (let [[r g b] (map (comp js/Math.floor (partial * 200) :amplitude) phasors)]
                                     (str "rgb(" r "," g "," b ")")))
                         draw-curve! (fn [{:as model :keys [drawing mode curve]} dt]
                                       (when curve
                                         (let [vxs (.-vertices curve) size (.-length vxs)]
                                           (case mode
                                             0 ;; spirograph
                                             (.push vxs (pen-position model))
                                             1 ;; fourier
                                             (doto vxs
                                               (.push (j/assoc! (pen-position model) :x (/ (.-width drawing) 2)))
                                               (.forEach (fn [p] (j/update! p :x - dt))))
                                             nil)
                                           (when (< MAXV size) (.splice vxs 0 (- size MAXV)))
                                           (j/assoc! curve :stroke (->color model)))))
                         apply-model (fn [{:as model :keys [clean? curve drawing phasors]} dt]
                                       (doseq [rot phasors] (update-phasor! rot dt))
                                       (js/requestAnimationFrame #(draw-curve! model dt)) ;; draw curve at next tick
                                       (when clean? (.remove drawing curve))
                                       (cond-> model clean? build-curve))
                         update! (fn [_frames dt] (swap! model apply-model (* time-scale dt)))
                         refn (fn [el]
                                (when (and el (not (:drawing @model)))
                                  (let [drawing (Two. (j/obj :type (.. Two -Types -svg) :autostart true :fitted true))]
                                    (.appendTo drawing el)
                                    (.bind drawing "update" update!)
                                    (swap! model #(-> % (assoc :drawing drawing) build-phasors build-curve)))))]
                        [:div {:ref refn :style {:width "100%" :height "800px"}}]))]))})


;; and finally our spinning wheels in place for a fourieristic drawing.

^{::clerk/width :full
  ::clerk/visibility :hide
  ::clerk/viewer spirograph-viewer}
(Object.)

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 7779})
  (clerk/clear-cache!)
  @model

  (remove-tap osc-message-handler)

  (.start osc)
  (.isListening osc)
  (.stopListening osc)

  (update-model (fn [m] (assoc-in m [:phasors 0 :amplitude] 2.0)))
  (update-model (fn [m] (assoc-in m [:phasors 1 :frequency] 0.9)))

  ;; save nice models
  (do
    (reset! model

            #_ {:mode 0,
                :phasors [{:amplitude 0.77, :frequency 0.34, :color "#f43f5e"}
                         {:amplitude 0.61, :frequency -0.21, :color "#65a30d"}
                         {:amplitude 0.24, :frequency 0.32, :color "#4338ca"}]}
            #_ {:mode 0
                :phasors [{:amplitude 0.41, :frequency 0.46, :color "#f43f5e"}
                      {:amplitude 0.71, :frequency -0.44, :color "#65a30d"}
                      {:amplitude 0.6, :frequency -0.45, :color "#4338ca"}]}

            #_ {:mode 0
             :phasors [{:amplitude 0.57, :frequency 0.39, :color "#f43f5e"}
                        {:amplitude 0.5, :frequency -0.27, :color "#65a30d"}
                        {:amplitude 0.125, :frequency 0.27, :color "#4338ca"}]}

            #_ {:mode 0,
                :phasors [{:amplitude 0.72, :frequency -0.25, :color "#f43f5e"}
                         {:amplitude 0.59, :frequency 0.45, :color "#65a30d"}
                         {:amplitude 0.52, :frequency 0.3, :color "#4338ca"}]}

            {:mode 0,
             :phasors [{:amplitude 0.80, :frequency 0.55, :color "#f43f5e"}
                       {:amplitude 0.5, :frequency -0.27, :color "#65a30d"}
                       {:amplitude 0.75, :frequency 0.27, :color "#4338ca"}]})
    (clerk/recompute!))

  ;; clean
  (do (swap! model assoc :clean? true)
      (clerk/recompute!)
      (swap! model assoc :clean? false)
      (clerk/recompute!)))
