(defproject yandere-launcher "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [enlive "1.1.6"]
                 [org.clojure/core.async "0.2.374"]
                 [seesaw "1.4.5"]
                 [commons-codec/commons-codec "1.10"]]
  :main ^:skip-aot yandere-launcher.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-vanity "0.2.0"]])
