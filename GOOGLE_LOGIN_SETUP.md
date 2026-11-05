# Turn on "Continue with Google" (one-time setup, about 10 minutes)

The button is already in the app. It stays inactive until you give the app a Google **client ID**.
Google requires this for every app that offers Google sign-in. Nobody else can create it for you.

## 1. Create the client IDs (Google Cloud Console)
1. Go to https://console.cloud.google.com and create a project (or pick one).
2. **APIs & Services > OAuth consent screen**: choose *External*, fill in the app name (iResponse) and your email, save.
   While the app is in *Testing*, add the Gmail accounts that will try it under **Test users**.
3. **APIs & Services > Credentials > Create credentials > OAuth client ID**, make **two** of them:
   - **Web application**: any name. Copy its **Client ID** (ends in `.apps.googleusercontent.com`). This is the one the app uses.
   - **Android**: package name `com.irespons.redesign` and the **SHA-1** of the key that signs your APK (next step).
     Make one Android client for the debug key and, if you ever use another key, one more for that key.

## 2. Get the SHA-1
This project signs the release APK with the debug key, so the debug key's SHA-1 is the one you need.
In Android Studio: **Gradle panel > app > Tasks > android > signingReport**, copy the `SHA1` line.
Or in a terminal: `./gradlew signingReport`.

## 3. Put the Web client ID in the app
Open `app/src/main/res/values/strings.xml` and replace the value of `google_web_client_id`:

    <string name="google_web_client_id" translatable="false">1234567890-abc123.apps.googleusercontent.com</string>

Build and run. The phone needs a Google account added in its Settings and internet when signing in.

## How it behaves
- Existing Google user: signs straight in.
- New Google user: asked for the same details as everyone else (mobile number, ID photo, face scan), no password, then signed in.
- A Google account becomes a **Citizen** account. Responder and admin accounts are still made by the Command Center.
- The account lives on this phone, like every other account. It is not shared with other phones.

## If it does not work
- "Google sign-in did not work": the Android client ID is missing or the SHA-1 / package name does not match. Re-check step 1 and 2.
- "No Google account was found": add a Google account in the phone's Settings.
- Account chooser never opens: the Web client ID in `strings.xml` is wrong or still the placeholder.
