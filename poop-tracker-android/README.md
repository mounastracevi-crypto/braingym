# Poop Tracker (Android)

A simple Android app to track bowel movements, similar to a period tracker workflow:

- Tap **"I pooped now"** whenever you poop
- Add a **past poop** (date + time) if you forgot to log on time
- Choose a **Bristol stool type** (1-7) for each entry
- See when your **last poop** happened
- Review your **history**
- Get a quick **regularity** and **constipation** status
- Track **daily and weekly chart trends**
- Set a **daily reminder notification**

## Features

1. **One-tap poop logging** with Bristol stool type selection
2. **Backfill support** to log earlier poop events by date/time
3. **Persistent history** using `SharedPreferences`
4. **Last poop time** + latest stool type
5. **Regularity summary** based on gaps between events
6. **Constipation check**
   - Possible constipation: no poop for 48+ hours
   - Likely constipation: no poop for 72+ hours
7. **Daily chart** (last 7 days)
8. **Weekly chart** (last 8 weeks)
9. **Daily reminder notifications**
   - Configurable reminder time
   - Rescheduled automatically after reboot

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

- Events are stored in JSON as:

```json
{
  "timestamp": 1739872800000,
  "bristolType": 4
}
```

- The app also supports backward compatibility with older saved entries that only stored timestamps.
- Regularity is considered **"Regular pattern"** when all observed intervals are between **12 and 48 hours**.
- If intervals fall outside that range, the status shows **"Irregular pattern"**.
- Daily chart bars represent poop counts for the last 7 calendar days.
- Weekly chart bars represent poop counts for the last 8 weeks (Monday-Sunday weeks).

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
