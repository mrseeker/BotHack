; The screen scraper handles redraw events, tries to determine when the frame is completely drawn and sends off higher-level events.  It looks for prompts and menus and triggers appropriate action selection commands.

(ns anbf.scraper
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [anbf.util :refer :all]
            [anbf.delegator :refer :all]))

(defn- nth-line
  "Returns the line of text on n-th line of the frame."
  [frame n]
  (nth (:lines frame) n))

(defn- topline [frame]
  (nth-line frame 0))

(defn- topline-empty? [frame]
  (re-seq #"^ +$" (topline frame)))

(defn- cursor-line
  "Returns the line of text where the cursor is on the frame."
  [frame]
  (nth-line frame (:cursor-y frame)))

(defn- before-cursor
  "Returns the part of the line to the left of the cursor."
  [frame]
  (subs (cursor-line frame) 0 (:cursor-x frame)))

(defn- before-cursor?
  "Returns true if the given text appears just before the cursor."
  [frame text]
  (.endsWith (before-cursor frame) text))

(defn- status-drawn?
  "Does the status line look fully drawn? Presumes there are no menus in the frame. Probably not 100% reliable."
  [frame]
  (let [last-line (nth-line frame 23)
        name-line (nth-line frame 22)]
    (and (< (:cursor-y frame) 22)
         (re-seq #" T:[0-9]+ " last-line)
         ; status may overflow
         (or (not= \space (nth name-line 78))
             (not= \space (nth name-line 79))
             (re-seq #" S:[0-9]+" name-line)))))

(defn- menu?
  "Is there a menu drawn onscreen?"
  [frame]
  (re-seq #"\(end\) $|\([0-9]+ of [0-9]+\)$" (before-cursor frame)))

(defn- choice-prompt
  "If there is a single-letter prompt active, return the prompt text, else nil."
  [frame]
  (if (<= (:cursor-y frame) 1)
    (re-seq #".*\? \[[^\]]+\] (\(.\) )?$" (before-cursor frame))))

(defn- more-prompt
  "Returns the whole text before a --More-- prompt, or nil if there is none."
  [frame]
  (when (before-cursor? frame "--More--")
    (-> (:lines frame)
        (nth 0)
        string/trim
        (string/replace-first #"--More--" ""))))

(defn- location-prompt? [frame]
  (let [topline (topline frame)]
    (or (re-seq #"^Unknown direction: ''' (use hjkl or \.)" topline)
        (re-seq #"(^ *|  ) (.*?)  \(For instructions type a \?\) *$" topline))))

(defn- prompt
  [frame]
  (when (and (<= (:cursor-y frame) 1)
             (before-cursor? frame "##'"))
    (let [prompt-end (subs (cursor-line frame) 0 (- (:cursor-x frame) 4))]
      (if (pos? (:cursor-y frame))
        (str (string/trim topline) " " prompt-end)
        prompt-end))))

(defn- prompt-fn
  [msg]
  (throw (UnsupportedOperationException. "TODO prompt-fn - implement me"))
  ; TODO
;    qr/^For what do you wish\?/         => 'wish',
;    qr/^What do you want to add to the (?:writing|engraving|grafitti|scrawl|text) (?:     in|on|melted into) the (.*?) here\?/ => 'write_what',
;    qr/^"Hello stranger, who are you\?"/ => 'vault_guard',
;    qr/^How much will you offer\?/      => 'donate',
;    qr/^What monster do you want to genocide\?/ => 'genocide_species',
;    qr/^What class of monsters do you wish to genocide\?/ => 'genocide_class',
  )

(defn new-scraper [{:keys [delegator] :as anbf}]
  (letfn [(handle-game-start [frame]
            (when (and (.startsWith (nth-line frame 1) "NetHack, Copyright")
                       (before-cursor? frame "] "))
              (log/debug "Handling game start")
              (condp #(.startsWith %2 %1) (cursor-line frame)
                "There is already a game in progress under your name."
                (send-off delegator write "y\n") ; destroy old game
                "Shall I pick a character"
                (send-off delegator choose-character)
                true)))
          (handle-choice-prompt [frame]
            (when-let [text (choice-prompt frame)]
              (log/debug "Handling choice prompt")
              ; TODO maybe will need extra newline to push response away from ctrl-p message handler
              ; TODO after-choice-prompt scraper state to catch exceptions (without handle-more)? ("You don't have that object", "You don't have anything to XXX")
              (throw (UnsupportedOperationException. "TODO choice prompt - implement me")))) ; TODO
          (handle-more [frame]
            (when-let [text (more-prompt frame)]
              (log/debug "Handling --More-- prompt")
              ; XXX TODO possibly update map and/or botl?
              (let [res (if (= text "You don't have that object.")
                          handle-choice-prompt
                          (do (send-off delegator message text) initial))]
                (send-off delegator write " ")
                res)))
          (handle-menu [frame]
            (when (menu? frame)
              (log/debug "Handling menu")
              (throw (UnsupportedOperationException. "TODO menu - implement me"))))
          (handle-direction [frame]
            (when (and (zero? (:cursor-y frame))
                       (before-cursor? frame "In what direction? "))
              (log/debug "Handling direction")
              (throw (UnsupportedOperationException. "TODO direction prompt - implement me"))))
          (handle-location [frame]
            (when (location-prompt? frame)
              (log/debug "Handling location")
              (throw (UnsupportedOperationException. "TODO location prompt - implement me"))))
          (handle-last-message [frame]
            (let [msg (-> frame topline string/trim)]
              (when-not (= msg "# #'")
                (send-off delegator message msg))))
          (handle-prompt [frame]
            (when-let [msg (prompt frame)]
              (send-off delegator write (str backspace backspace backspace))
              (send-off delegator (prompt-fn msg) msg)))

          (initial [frame]
            (log/debug "scraping frame")
            (or (handle-game-start frame)
                (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                ;(handle-direction frame) ; XXX TODO (nevykresleny) handle-direction se zrusi pri ##
                (handle-location frame)
                ; pokud je vykresleny status, nic z predchoziho nesmi nezotavitelne/nerozpoznatelne reagovat na "##"
                (when (status-drawn? frame)
                  (log/debug "writing ##' mark")
                  (send-off delegator write "##'")
                  marked)
                (log/debug "expecting further redraw")))
          ; odeslal jsem marker, cekam jak se vykresli
          (marked [frame]
            (log/debug "marked scraping frame")
            ; veci co se daji bezpecne potvrdit pomoci ## muzou byt jen tady, ve druhem to muze byt zkratka, kdyz se vykresleni stihne - pak se ale hur odladi spolehlivost tady
            ; tady (v obou scraperech) musi byt veci, ktere se nijak nezmeni pri ##'
            (or (handle-more frame)
                (handle-menu frame)
                (handle-choice-prompt frame)
                (handle-prompt frame)
                (when (and (= 0 (:cursor-y frame))
                           (before-cursor? frame "# #'"))
                  (send-off delegator write (str backspace \newline \newline))
                  (log/debug "persisted mark")
                  lastmsg-clear)
                (log/debug "marked expecting further redraw")))
          (lastmsg-clear [frame]
            (log/debug "scanning for cancelled mark")
            (when (topline-empty? frame)
              (log/debug "ctrl+p ctrl+p")
              (send-off delegator write (str (ctrl \p) (ctrl \p)))
              lastmsg-get))
          ; jakmile je vykresleno "# #" na topline, mam jistotu, ze po dalsim ctrl+p se vykresli posledni herni zprava (nebo "#", pokud zadna nebyla)
          (lastmsg-get [frame]
            (log/debug "scanning ctrl+p")
            (when (re-seq #"^# # +" (topline frame))
              (log/debug "got second ctrl+p, sending last ctrl+p")
              (send-off delegator write (str (ctrl \p)))
              lastmsg+action))
          ; cekam na vysledek <ctrl+p>, bud # z predchoziho kola nebo presmahnuta message
          (lastmsg+action [frame]
            (log/debug "scanning for last message")
            (or (when-not (or (= (:cursor-y frame) 0)
                              (topline-empty? frame))
                  (if-not (re-seq #"^# +" (topline frame))
                    (send-off delegator message (string/trim (topline frame)))
                    (log/debug "no last message"))
                  (log/debug "publishing full frame")
                  (-> delegator
                      (send-off full-frame frame)
                      (send-off choose-action anbf))
                  initial)
                (log/debug "lastmsg expecting further redraw")))]
    initial))

(defn- apply-scraper
  "If the current scraper returns a function when applied to the frame, the function becomes the new scraper, otherwise the current scraper remains.  A fresh scraper is created and applied if the current scraper is nil."
  [current-scraper anbf frame]
  (let [next-scraper ((or current-scraper (new-scraper anbf)) frame)]
    (if (fn? next-scraper)
      next-scraper
      current-scraper)))

(defn scraper-handler [anbf]
  (reify RedrawHandler
    (redraw [_ frame]
      (->> (dosync (alter (:scraper anbf) apply-scraper anbf frame))
           type
           (log/debug "next scraper:")))))
