(ns spellchecker-backend.core
  (:require
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.cors :refer [wrap-cors]]
   [spellchecker-backend.handler :refer [app]])
  (:gen-class))

(def wrapped-app
  (wrap-cors app
             :access-control-allow-origin [#"http://localhost:8280" #".*"]
             :access-control-allow-methods [:get :post :options]
             :access-control-allow-headers ["Content-Type"
                                            "Authorization"
                                            "Accept"
                                            "Origin"]))

(defonce server* (atom nil))

(defn start!
  ([] (start! 3000))
  ([port]
   (when @server*
     (.stop @server*))
   (println "Starting server on port" port)
   (reset! server*
           (run-jetty wrapped-app {:port port :join? false}))))

(defn stop! []
  (when-let [s @server*]
    (println "Stopping server")
    (.stop s)
    (reset! server* nil)))

(defn -main [& [port-str]]
  (let [port (some-> port-str Integer/parseInt)]
    (start! (or port 3000))
    (println "Server running. Press Ctrl+C to exit.")
    @(promise)))