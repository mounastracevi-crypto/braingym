# Poop Tracker (Android)

A simple Android app to track bowel movements, similar to a period tracker workflow:

- Tap **"I pooped now"** whenever you poop
- See when your **last poop** happened
- Review your **history**
- Get a quick **regularity** and **constipation** status

## Features

1. **One-tap poop logging**
2. **Persistent history** using `SharedPreferences`
3. **Last poop time** with relative elapsed time
4. **Regularity summary** based on gaps between events
5. **Constipation check**
   - Possible constipation: no poop for 48+ hours
   - Likely constipation: no poop for 72+ hours

## Project Structure

```text
poop-tracker-android/
  app/
    src/main/java/com/example/pooptracker/MainActivity.kt
    src/main/res/layout/activity_main.xml
    src/main/res/values/strings.xml
    src/main/AndroidManifest.xml
```

## Logic Notes

- Events are stored as timestamps (`Long`) in a JSON array.
- Regularity is considered **"Regular pattern"** when all observed intervals are between **12 and 48 hours**.
- If intervals fall outside that range, the status shows **"Irregular pattern"**.

## Run in Android Studio

1. Open `poop-tracker-android` in Android Studio.
2. Let Gradle sync dependencies.
3. Run on an emulator or Android device (API 26+).

## Build from Terminal

```bash
cd poop-tracker-android
./gradlew help
./gradlew assembleDebug
```

> You need a valid Android SDK installation (`ANDROID_HOME` / `ANDROID_SDK_ROOT` or `local.properties` with `sdk.dir`).

## Future Improvements

- Optional poop notes (Bristol stool scale, pain level, food triggers)
- Chart view over weekly/monthly trends
- Notifications/reminders
- Export/import history
