# Talking Alarm

A personal Android alarm app. Set as many alarms per day as you like, write what each
one is for, and at that time the phone speaks your text out loud instead of just beeping.

## What it does

- Unlimited alarms, each with its own time and its own message.
- At alarm time it says: *"It's 7:30 AM. Take your medicine and drink water."* and keeps
  repeating until you press Stop, or until 5 minutes pass.
- Uses your phone's built-in text-to-speech, so it works offline once a voice is installed
  and it can speak whatever language your phone is set to.
- Repeat on chosen weekdays, or fire once and switch itself off.
- Rings over the lock screen with a full screen Stop / Snooze screen, at full alarm volume,
  with vibration. Survives Doze and reboots.
- "Hear it" button so you can preview the message while writing it.

## Sound options

Each alarm picks how it wakes you, in the editor under "Sound":

- **Speak my text** — reads your note aloud, on repeat. The original behaviour.
- **Ringtone** — plays a sound from your phone instead, chosen with "Choose sound".
  The picker lists your system alarm sounds and ringtones, plus any audio you've added.
- **Both** — plays the sound for five seconds, speaks your text, and alternates.

The **Voice** tab controls how the speaking sounds, for alarms and the timer alike.
It lists every voice your phone's speech engine has installed for your language; tap one
to use it, or the play icon to hear it first. Speed and pitch sliders sit underneath.

Most phones ship with two or three voices. To get more, use the "Open speech settings"
button at the bottom of that tab: choose your engine (Google's is the usual one), then
install additional voice data or another language. Anything you add there shows up in the
app's list straight away. Installing the Google Speech Services app from the Play Store
also tends to add several.

## Interval timer

The second tab is a repeating timer for workouts and anything else on a fixed rhythm.
Pick a gap (presets from 10 seconds to 2 minutes, or type your own), write what it should
say each time, and press Start. It beeps, then speaks, every time the gap elapses.

- Optionally counts rounds out loud: "Round 4. Switch sides."
- Optionally stops itself after a set number of rounds, otherwise it runs until you stop it.
- Pause and resume, from the app or from the notification.
- Keeps running with the screen off and the phone in your pocket.
- Minimum gap is 3 seconds, since anything shorter can't finish a sentence.

## Updating an app you already installed

Upload the new project files over the old ones in your GitHub repository (same
**Add file → Upload files** page, drag the folders in, Commit changes). The build runs
again on its own and replaces the APK in Releases. Download and install it the same way —
Android updates the existing app and your saved alarms stay put.

The project carries its own signing key at `app/keystore.jks`, which is why updates install
cleanly over each other. Without it, every cloud build would use a randomly generated key
and Android would reject the update with "App not installed". The key's password sits in
`app/build.gradle.kts` in plain sight: deliberate, and fine for an app only you install,
but you would never do this for something published to the Play Store.

## Getting the APK without installing Android Studio

The repo includes `.github/workflows/build-apk.yml`, which builds the app on GitHub's
servers for free.

1. Make a free GitHub account and create a new **private** repository.
2. Upload the contents of this folder to it (on github.com use **Add file → Upload files**
   and drag the whole unzipped folder in, or use `git push` if you have git).
3. Open the **Actions** tab. The build starts by itself and takes about 3–5 minutes.
4. When it finishes, go to the **Releases** section on the repo home page and download
   `app-debug.apk` straight onto your phone.
5. Open the downloaded file. Android will ask you to allow installs from your browser —
   say yes, then install.

The APK is debug-signed, which is exactly what you want for your own phone. It just means
it can't be published to the Play Store, and that reinstalling a differently-signed build
later would need an uninstall first.

## Building it in Android Studio

You need **Android Studio** (Hedgehog or newer) and a phone with **Developer options →
USB debugging** switched on.

1. Unzip `TalkingAlarm.zip` somewhere.
2. Android Studio → **File → Open** → pick the `TalkingAlarm` folder.
3. Let Gradle sync. The zip has no `gradle-wrapper.jar` (binary files can't be shipped as
   text), so if Studio complains about the wrapper, either accept its offer to generate it,
   or go to **File → Project Structure → Project** and pick Gradle 8.7. If Studio offers to
   upgrade AGP/Gradle/Kotlin, accept — the code doesn't depend on the exact versions.
4. Plug in your phone, pick it in the device dropdown, press **Run** (green ▶).

That installs the app on your phone. It stays installed; you only need Android Studio again
if you want to change something.

## After installing — three settings that matter

Android will silently delay alarms if you skip these.

1. **Notifications** — the app asks on first launch. Say allow.
2. **Background running** — the app shows a card with a button for this. Tap it and choose
   "Allow". On Xiaomi / Realme / Oppo / Vivo / Samsung phones also go to
   *Settings → Apps → Talking Alarm → Battery* and set it to Unrestricted, and lock the app
   in the recents screen.
3. **Text-to-speech voice** — *Settings → System → Languages & input → Text-to-speech
   output*. Open the engine settings and install the offline voice data for the language you
   write your alarms in. If you write in Hindi, install the Hindi voice and set your phone
   language accordingly, otherwise it will be read with an English pronunciation.

On Android 14+, if the alarm screen doesn't appear over the lock screen, go to
*Settings → Apps → Talking Alarm → Full screen notifications* (sometimes called
"Display over other apps") and enable it.

## Using it

Tap **+**, tap the time to set it, type what the alarm should say, tap the weekday chips if
it should repeat, then **Save alarm**. Tap any alarm to edit it, the ▶ icon to hear it, the
switch to turn it off.

## Changing things yourself

Everything lives in `app/src/main/java/com/example/talkingalarm/`:

- `AlarmService.kt` — the speaking. `SNOOZE_MINUTES` (5), `AUTO_STOP_MS` (how long it keeps
  talking before giving up, 5 min), `GAP_BETWEEN_REPEATS_MS` (pause between repeats, 1.5 s),
  `setSpeechRate(0.95f)` (speed), and the sentence built in `speakOnce()`.
- `AlarmScheduler.kt` — when alarms fire.
- `MainActivity.kt` — the list and the editor screen.
- `Theme.kt` — colours.

Alarms are stored in SharedPreferences as JSON, so there's no database to set up.

Built for personal use on your own phone — it isn't signed or configured for the Play Store.
