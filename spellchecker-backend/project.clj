(defproject spellchecker-backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [compojure "1.7.2"]
                 [ring/ring-defaults "0.7.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [ring-cors "0.1.13"]
                 [org.clojure/data.csv "1.1.0"]
                 [clj-http "3.13.1"]
                 [cheshire "6.1.0"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [metosin/ring-swagger-ui "4.15.5"]]
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler spellchecker-backend.handler/app}
  :main spellchecker-backend.core
  :repl-options {:init-ns user}
  :profiles
  {:dev {:source-paths ["dev"]
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.6.2"]
                        [org.clojure/tools.namespace "1.4.5"]]}})
