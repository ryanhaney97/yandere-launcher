(ns yandere-launcher.ui
  (:require
   [clojure.core.async
    :as a
    :refer [>! <! >!! <!! go chan buffer close! thread
            alts! alts!! timeout]]
   [seesaw.core :as ss]
   [clojure.java.io :as io])
  (:import
   [java.io Writer BufferedReader InputStreamReader]
   [javax.swing JTextArea]))

(def event-chan (chan))

(defn panel-writer [^JTextArea text]
  (proxy [Writer] []
    (write
     ([carray offset length]
      (.append text (subs (apply str carray) offset (+ offset length))))
     ([data]
      (.append text (str data))))
    (flush []
           nil)
    (close []
           nil)))

(def console-output (ss/text
                     :text ""
                     :editable? false
                     :multi-line? true
                     :wrap-lines? true
                     :size [1070 :by 630]))

(def coutput-stream (panel-writer console-output))

(defn cprintln [s]
  (binding [*out* coutput-stream]
    (println s)))

(defn handle-output [process]
  (go
   (let [reader (BufferedReader. (InputStreamReader. (.getInputStream process)))]
     (try
       (loop [line (.readLine reader)]
         (when line
           (cprintln line)
           (recur (.readLine reader))))
       (catch Exception e
         (.printStackTrace e)
         (System/exit 1))))))

(defn process-waiter [process]
  (thread
   (.waitFor process)
   (>!! event-chan {:event :done})))

(defn run-yandere-simulator [_]
  (let [folder (io/file "YandereSimulator")]
    (if (and (.exists folder) (.isDirectory folder))
      (let [os (System/getProperty "os.name")
            executable (first (filter #(.contains %1 ".exe") (map #(.getAbsolutePath %1) (file-seq folder))))
            executable (if (not (.contains os "Windows"))
                         (str "wine " executable)
                         executable)]
        (>!! event-chan {:event :running})
        (cprintln (str "Running Executable: " executable))
        (let [process (.exec (Runtime/getRuntime) executable)]
          (handle-output process)
          (process-waiter process)))
      (cprintln "Unable to find Yandere Simulator!"))))

(def run-button
  (ss/button
   :text "Checking for Updates..."
   :listen [:action run-yandere-simulator]
   :enabled? false
   :size [200 :by 50]))

(def progress-bar
  (ss/progress-bar
   :visible? false
   :size [1070 :by 5]))

(def yandere-frame (ss/frame
                    :title "Yandere Launcher"
                    :content (ss/flow-panel
                              :items [console-output
                                      progress-bar
                                      run-button]
                              :hgap 0
                              :vgap 5)
                    :size [1080 :by 720]
                    :resizable? false
                    :on-close :exit))

(defn update-progress-bar [{:keys [current total]}]
  (ss/config! progress-bar :value current :max total))

(a/go-loop
 [event (<! event-chan)]
 (let [event-name (:event event)]
   (condp = event-name
     :updating (do
                 (ss/config! run-button :text "Updating...")
                 (ss/config! console-output :size [1070 :by 620])
                 (ss/show! progress-bar))
     :progress (update-progress-bar event)
     :running (ss/config! run-button :text "Running..." :enabled? false)
     :finished-downloading (do
                             (ss/hide! progress-bar)
                             (ss/config! console-output :size [1070 :by 630]))
     :done (ss/config! run-button :text "Play!" :enabled? true)))
 (recur (<! event-chan)))

(defn initialize-ui []
  (ss/show!
   yandere-frame))
