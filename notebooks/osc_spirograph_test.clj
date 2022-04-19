;; # ꩜ OSC Spirograph Test
(ns osc-spirograph-test
  (:require [nextjournal.clerk :as clerk]))

^{::clerk/visibility :show
  ::clerk/no-cache true
  ::clerk/viewer {:fetch-fn (fn [_ x] x)
                  :transform-fn (fn [{::clerk/keys [var-from-def]}] {:value @@var-from-def})
                  :render-fn '(fn [{:keys [value]}]
                                (js/console.log :model/update (pr-str value))
                                ;; update model on every re-render
                                (defonce model (reagent/atom nil))
                                (pr-str (swap! model
                                               (partial merge-with #(mapv merge %1 %2))
                                               value)))}}
(def model
  (atom {:rotors
         [{:r 1.0 :omega 0.1 :color "red"}
          {:r 0.5 :omega -1.4 :color "green"}
          {:r 0.25 :omega 1.0 :color "blue"}]}))


^{::clerk/viewer {:render-fn '(fn [_]
                                (v/html [osc/spirograph {:model model}]))}}
(Object.)


(defn update-model! [f]
  (swap! model f)
  (binding [*ns* (find-ns 'osc-spirograph)]
    (clerk/recompute!)))

(mapv merge [{:a 1 :b 1} {:c 1 :d 1}] [{:a 2 :b 2} {:c 2 :d 2}])

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (update-model! (fn [m] (assoc-in m [:rotors 0 :r] 1.0)))
  (update-model! (fn [m] (assoc-in m [:rotors 1 :omega] 1.7)))

  (swap! model assoc-in [:rotors 0 :r 0.5])

  (clerk/serve! {:port 7777})
  (clerk/clear-cache!)
  (shadow.cljs.devtools.api/repl :browser))
