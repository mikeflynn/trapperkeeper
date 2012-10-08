(defproject trapperkeeper "0.0.1-ALPHA"
  :description "A clojure image server."
  :url "https://github.com/mikeflynn/trapperkeeper"
  :aot [trapperkeeper.core]
  :dependencies [[org.clojure/clojure "1.3.0"]
                [compojure "1.1.3"]
                [log4j "1.2.16"]
                [org.clojure/tools.logging "0.2.3"]
                [cheshire "4.0.0"]]
  :plugins [[lein-ring "0.6.2"]]
  :ring {:handler trapperkeeper.core/app
         :init trapperkeeper.core/init-app}
  :resources-path "resources")