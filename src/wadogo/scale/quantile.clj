(ns wadogo.scale.quantile
  (:require [fastmath.core :as m]
            [fastmath.stats :as stats]
            [wadogo.common :refer [scale ->ScaleType strip-keys]]
            [wadogo.utils :refer [interval-steps-before values->reversed-map]]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(m/use-primitive-operators)

(def ^:private quantile-params
  {:range 4
   :estimation-strategy :legacy})

(defn- build-quantiles
  [r]
  (if (sequential? r)
    (if (every? (fn [v] (and (number? v)
                            (pos? ^double v)
                            (<= ^double v 1.0))) r)
      [true r]
      [false (rest (m/slice-range (inc (count r))))])
    [true (rest (m/slice-range (inc (long r))))]))

(defmethod scale :quantile
  ([_] (scale :quantile {}))
  ([_ params]
   (assert (seq (:domain params)) "Domain can't be empty, please provide any data as a sequence of numbers")
   (let [params (merge quantile-params params)
         xs (m/seq->double-array (remove nil? (:domain params)))
         [^double start ^double end] (stats/extent xs)
         r (:range params)
         [quantiles? quantiles] (build-quantiles r)

         steps (stats/quantiles xs quantiles (:estimation-strategy params))
         steps-corr (conj (seq steps) start)
         step-fn (interval-steps-before steps)
         ids (->> xs (map step-fn) frequencies)
         values (mapv (fn [id [^double x1 ^double x2] q]
                        {:dstart x1
                         :dend x2
                         :value (if quantiles? q (nth r id))
                         :count (ids id)
                         :quantile q}) (range) (partition 2 1 steps-corr) quantiles)
         forward (comp values step-fn)]
     (->ScaleType :quantile xs (if quantiles? quantiles r) (:ticks params) (:fmt params)
                  (fn local-forward
                    ([^double v interval?]
                     (let [res (when (<= start v end) (forward v))]
                       (if interval? res (:value res))))
                    ([^double v] (local-forward v false)))
                  (values->reversed-map values :value)
                  (assoc (strip-keys params) :quantiles (butlast (map (juxt :dend :quantile) values)))))))
