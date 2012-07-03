(defproject trapperkeeper "0.0.1-ALPHA"
  :description "A clojure image server."
  :url "https://github.com/mikeflynn/trapperkeeper"
  :dependencies [[org.clojure/clojure "1.3.0"]
  				[compojure "1.0.1"]]
  :dev-dependencies [[lein-ring "0.6.2"]
  :ring {:handler trapperkeeper.core/app
         :init    trapperkeeper.core/init-app})