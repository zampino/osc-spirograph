;; # ꩜ OSC Spirograph
(ns osc-spirograph
  (:require [nextjournal.clerk :as clerk])
  (:import (com.illposed.osc ConsoleEchoServer OSCMessageListener OSCMessageEvent OSCMessage)
           (com.illposed.osc.messageselector JavaRegexAddressMessageSelector)
           (org.slf4j LoggerFactory)
           (java.net InetSocketAddress)))

;; We start by initializing an OSC Server. We're overlaying an extra broadcast (clojure tap) of received OSC messages on top of a simple echo server. We wrap it in a future it only for clerk's `build-static-app!` not to hang.
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
                                (pr-str (swap! model
                                               (partial merge-with (fn [old new] (if (vector? old) (mapv merge old new) new)))
                                               value)))}}
(def model
  (atom {:mode 0
         :rotors [{:r 1.0 :omega 0.08 :color "#f43f5e"}
                  {:r 0.5 :omega -0.23 :color "#65a30d"}
                  {:r 0.25 :omega 0.5 :color "#4338ca"}]}))

;; Update model and recompute the notebook.

(defn update-model! [f]
  (swap! model f)
  (binding [*ns* (find-ns 'osc-spirograph)]
    (clerk/recompute!)))

;; We configured our OSC message arguments to always be a vector of the form
;;     [value & path]
;; where value is changed by the controller we're interacting with, while the tail of the arguments is a valid path in the model above. In this example we're ignoring the OSC message address.
;;
;; This is how our interface is looking ![interface](https://github.com/zampino/osc-spirograph/tree/main/spirograph.png).

(defn osc->map [^OSCMessage m]
  (let [[v & path] (map #(cond-> % (string? %) keyword) (.getArguments m))]
    {:value v :path path}))

(defn osc-message-handler [osc-message]
  (let [{:keys [path value]} (osc->map osc-message)]
    (update-model! #(assoc-in % path value))))

;; This needs to be evaluated just once (Clerk caching should take care of this, why do I need the defonce?).
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
                         R 200 MAXV 1000 T (* js/Math.PI 0.05)
                         rotor-group (fn [drawing parent {:keys [r color]}]
                                       (let [G (doto (Group.)
                                                 (j/assoc! :position
                                                           (j/get-in parent [:children 0 :vertices 1]
                                                                     (Vector. (/ (.-width drawing) 2)
                                                                              (/ (.-height drawing) 2)))))]
                                         (.add parent G)
                                         (.add G (doto (Line. 0.0 0.0 (* r R) 0.0)
                                                   (j/assoc! :linewidth 7)
                                                   (j/assoc! :stroke color)
                                                   (j/assoc! :cap "round")
                                                   ))
                                         G))
                         build-rotors (fn [{:as m :keys [drawing]}]
                                        (update m :rotors
                                                #(:rotors (reduce
                                                            (fn [{:as acc :keys [parent-group]} params]
                                                              (let [g (rotor-group drawing parent-group params)]
                                                                (-> acc
                                                                    (update :rotors conj (assoc params :group g))
                                                                    (assoc :parent-group g))))
                                                            {:parent-group (.-scene drawing) :rotors []}
                                                            %))))
                         update-rotor! (fn [{:keys [r omega group]} tdelta]
                                         (when group
                                           (j/assoc-in! group [:children 0 :vertices 1 :x] (* r R))
                                           (j/update! group :rotation + (* T omega tdelta))))
                         build-curve (fn [{:as m :keys [drawing]}]
                                       (assoc m :curve
                                                (doto (.makeCurve drawing)
                                                  (j/assoc! :closed false)
                                                  (j/assoc! :stroke "#5b21b6")
                                                  (j/assoc! :linewidth 5)
                                                  (j/assoc! :opacity 0.8)
                                                  .noFill)))
                         pen-position (fn [{:keys [rotors]}]
                                        (let [{:keys [r group]} (last rotors)]
                                          (.copy (Vector.)
                                                 (-> group world-matrix (.multiply (* r R) 0.0 1.0)))))
                         draw-curve! (fn [{:as model :keys [drawing mode curve]} tdelta]
                                       (when curve
                                         (let [vxs (.-vertices curve) size (.-length vxs)]
                                           (case mode
                                             0 ;; spirograph
                                             (.push vxs (pen-position model))
                                             1 ;; fourier
                                             (doto vxs
                                               (.push (j/assoc! (pen-position model) :x (/ (.-width drawing) 2)))
                                               (.forEach (fn [p] (j/update! p :x - tdelta))))
                                             nil)
                                           (when (< MAXV size) (.splice vxs 0 (- size MAXV))))))
                         apply-model (fn [{:as model :keys [clean? curve drawing rotors]} t tdelta]
                                       (doseq [rot rotors] (update-rotor! rot tdelta))
                                       ;; draw curve at next tick
                                       (js/requestAnimationFrame #(draw-curve! model tdelta))
                                       (when clean? (.remove drawing curve))
                                       (cond-> model clean? build-curve))
                         update! (fn [frames tdelta] (swap! model apply-model (* 0.09 frames) (* 0.09 tdelta)))
                         refn (fn [el]
                                (when (and el (not (:drawing @model)))
                                  (js/console.log :boot el)
                                  (let [drawing (Two. (j/obj :type (.. Two -Types -svg)
                                                             :autostart true
                                                             :fitted true))]
                                    (.appendTo drawing el)
                                    (.bind drawing "update" update!)
                                    (swap! model #(-> %
                                                      (assoc :drawing drawing)
                                                      build-rotors build-curve)))))]
                        [:div {:ref refn
                               :style {:width "100%" :height "800px"}}]))]))})


;; this value is only used to attach the definition of our spirograph component in the reagent context.
^{::clerk/width :full
  ::clerk/visibility :hide
  ::clerk/viewer spirograph-viewer}
(Object.)

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  @model
  (clerk/serve! {:port 7779})
  (clerk/clear-cache!)
  (clerk/build-static-app! {:browse? false
                            :bundle? false
                            :paths ["notebooks/osc_spirograph.clj"]})

  ;;this needs be registered just once

  (remove-tap osc-message-handler)

  (.start osc)
  (.isListening osc)
  (.stopListening osc)


  (update-model (fn [m] (assoc-in m [:rotors 0 :omega] 0.9)))
  (update-model (fn [m] (assoc-in m [:rotors 1 :omega] -0.9)))
  (update-model (fn [m] (assoc-in m [:rotors 2 :omega] 2.9)))
  (update-model (fn [m] (assoc-in m [:rotors 0 :r] 2.0)))

  (update-model (fn [m] (assoc-in m [:mode] :spirograph)))

  (swap! model assoc-in [:rotors 0 :r 0.5])

  (System/getenv "HOME")

  )
