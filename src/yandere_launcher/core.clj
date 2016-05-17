(ns yandere-launcher.core
  (:require
   [net.cgrand.enlive-html :as html]
   [clojure.core.async
    :as a
    :refer [>! <! >!! <!! go chan buffer close! thread
            alts! alts!! timeout]]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [yandere-launcher.ui :refer [initialize-ui cprintln handle-output event-chan]])
  (:import
   [java.net URL URLConnection]
   [java.io BufferedInputStream FileOutputStream FileInputStream File InputStreamReader BufferedReader]
   [java.text SimpleDateFormat]
   [java.util Locale]
   [org.apache.commons.codec.digest DigestUtils])
  (:gen-class))

(def download-page "https://yanderedev.wordpress.com/downloads/")

(defn get-download-page []
  (try
    (html/html-resource (URL. download-page))
    (catch Exception e
      (.printStackTrace e)
      (System/exit 1))))

(defn get-download-link [version-page]
  (get-in (first (flatten (html/select version-page [:p :strong :a]))) [:attrs :href]))

(defn extract-version [s]
  (apply str (interpose " " (rest (re-find #"(\D+)(\d+)" (subs s 10))))))

(defn get-current-version []
  (extract-version (first (string/split (last (string/split (get-download-link (get-download-page)) #"/")) #"\."))))

(defn get-downloaded-version []
  (let [folder (io/file (str "YandereSimulator" File/separator))]
    (when (.exists folder)
      (let [version-name (first (filter #(.contains %1 "YandereSim") (map #(.getName %1) (.listFiles folder))))]
        (extract-version version-name)))))

(defn get-date [s]
  (let [formatter (SimpleDateFormat. "MMMM d yyyy" Locale/ENGLISH)]
    (.parse formatter (str s " 2016"))))

(defn update? [web-version downloaded-version]
  (if (and web-version downloaded-version)
    (let [web-date (get-date web-version)
          downloaded-date (get-date downloaded-version)]
      (> (compare web-date downloaded-date) 0))
    true))

(defn fetch-mediafire-download [mediafire-page]
  (loop [data (string/split mediafire-page #"(?=\<)|(?<=\>)")]
    (if (empty? data)
      (cprintln "Error retreiving mediafire download!")
      (let [current-data (first data)]
        (if (.contains current-data "DLP_mOnDownload(this)")
          (let [result (subs current-data (+ (.indexOf current-data "href=\"") 6))]
            (subs result 0 (.indexOf result "\"")))
          (recur (rest data)))))))

(defn get-remote-file-size [url]
  (try
    (let [^URLConnection connection (.openConnection (URL. url))]
      (.connect connection)
      (let [size (.getContentLengthLong connection)]
        (.disconnect connection)
        size))
    (catch Exception e
      (.printStackTrace e)
      (System/exit 1))))

(defn track-progress [url file]
  (let [stop-chan (chan)
        total-size (get-remote-file-size url)
        file (io/file file)]
    (thread
     (loop [[result channel] (alts!! [stop-chan (timeout 200)])]
       (if (= result :stop)
         (>!! event-chan {:event :finished-downloading})
         (do
           (>!! event-chan {:event :progress
                            :current (.length file)
                            :total total-size})
           (recur (alts!! [stop-chan (timeout 200)]))))))
    stop-chan))

(defn save-url [url file]
  (cprintln "Downloading, please wait warmly until Yandere Simulator is ready...")
  (with-open [in (io/input-stream url)
              out (io/output-stream file)]
    (let [stop-chan (track-progress url file)]
      (io/copy in out)
      (>!! stop-chan :stop)
      (cprintln "Download Complete!")))
  file)

(defn gen-md5-checksum [file]
  (with-open [in (io/input-stream file)]
    (string/upper-case (DigestUtils/md5Hex in))))

(defn delete-folder [folder-name]
  (let [file (io/file folder-name)]
    (when (.exists file)
      (if (.isDirectory file)
        (doall (map delete-folder (map (partial io/file file) (.list file)))))
      (io/delete-file file))))

(defn winrar-extract-rar [file]
  (let [winrar "C:\\Program Files (x86)\\WinRAR\\Unrar.exe"
        winrar-file (io/file winrar)]
    (if (.exists winrar-file)
      (let [fullpath (.getAbsolutePath (io/file file))
            command (str winrar " x " fullpath " *.* YandereSimulator\\")]
        (delete-folder "YandereSimulator")
        (cprintln (str "Running Command: " command))
        (<!! (handle-output (.exec (Runtime/getRuntime) command)))
        true)
      false)))

(defn seven-zip-extract-rar [file]
  (let [seven-zip "C:\\Program Files\\7-Zip\\7z.exe"
        seven-zip-file (io/file seven-zip)]
    (if (.exists seven-zip-file)
      (let [fullpath (.getAbsolutePath (io/file file))
            command (str seven-zip " x " fullpath " -oYandereSimulator")]
        (delete-folder "YandereSimulator")
        (cprintln (str "Running Command: " command))
        (<!! (handle-output (.exec (Runtime/getRuntime) command)))
        true)
      false)))

(defn unarchiver-extract-rar [file]
  (let [fullpath (.getAbsolutePath (io/file file))
        command ["open" "-a" "The Unarchiver" fullpath]]
    (delete-folder "YandereSimulator")
    (cprintln (str "Running Command: " (apply str (interpose " " command))))
    (<!! (handle-output (.exec (Runtime/getRuntime) (into-array String command))))
    true))

(defn extract-rar [file]
  (cprintln "Beginning extraction...")
  (let [os (System/getProperty "os.name")]
    (if (.contains os "Windows")
      (if (not (seven-zip-extract-rar file))
        (if (not (winrar-extract-rar file))
          (cprintln "Unable to find WinRAR or 7zip in the default locations! Please install one of those 2 programs, or extract the contents of YandereSimulator.rar directly to a folder called YandereSimulator.")
          true)
        true)
      (if (.contains os "Mac")
        (unarchiver-extract-rar file)))))

(defn get-latest-version []
  (cprintln "Checking for updates...")
  (if (update? (get-current-version) (get-downloaded-version))
    (do
      (cprintln "Update detected, updating...")
      (>!! event-chan {:event :updating})
      (->
       (get-download-page)
       (get-download-link)
       (slurp)
       (fetch-mediafire-download)
       (save-url "YandereSimulator.rar")
       (extract-rar)
       (#(if %1
           (cprintln "Extraction Complete! Yandere Simulator is now ready.")))))
    (cprintln "Yandere Simulator is up to date.")))

(defn -main [& args]
  (initialize-ui)
  (get-latest-version)
  (>!! event-chan {:event :done}))
