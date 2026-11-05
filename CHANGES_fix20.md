# fix20: online service connected
- `net/Config.java`: DEFAULT_CLOUD_URL set to the irespons Firebase Realtime Database (Singapore).
- `res/values/strings.xml`: firebase_api_key set. Email/Password sign-in is enabled in the Firebase project.
- Everything else is the same as fix19 (fix13 map with road-following routes).
- Realtime Database test-mode rules expire on 2026-12-04. Replace them before then.
- Not compiled (no Android SDK).
