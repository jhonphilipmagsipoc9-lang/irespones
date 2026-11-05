# iResponse fix11: what changed

1. **Profile picture (optional)** on Register (citizens) and Create account (admins making responders/admins). Tap the circle, take a photo or pick one. Skip it and nothing changes.
2. **Everyone sees profile pictures**: admin, responder and citizen photos show in the Users list, chat bubbles, the responder card and Profile/Settings. A small copy (192 px) is shared through the server (`photos/` + `photoidx/`), and phones download only the pictures that changed.
3. **"Cannot reset here"** now says: *No account was found for that number. Please create an account first.* with a Create account button. The "ask the Command Center" text is gone.
4. **Settings: Map key (MapTiler) removed.** The map always uses the standard OpenStreetMap tiles.
5. **Online vs offline**
   - Online only: sign in, create account, forgot/change password, enable/disable/delete accounts, sending chat messages (responders and DRRMO/Command Center), the AI assistant.
   - After signing in the app opens offline. Logging out means the next sign-in needs internet again.
   - Offline still works: emergency buttons (auto call, direct call, SMS with location), maps, viewing reports.
   - Passwords are now checked and stored on the server (bcrypt), not compared on the phone.
6. **Location must be ON**: after sign-in the app shows "Turn on location" until the permission and the phone's location switch are both on. The emergency buttons ask first, but "Call anyway" is always there so help is never blocked.

## Server (important)
Copy the whole `server/` folder (now includes `auth.php`) to XAMPP, open Settings > Server address on the phone (or the prompt at sign-in) and enter the address of `index.php`.
- First start creates the Command Center account **100100 / admin123**. Change the password after the first sign-in.
- Accounts that only existed on a phone (for example your test responders) move to the server automatically the first time their owner signs in with the right password. After everyone has signed in once, set `'allow_migrate' => false` in `server/config.php`.
- Password reset: with no SMS service the app shows the code on screen (test mode). For real SMS, put your gateway URL in `sms_gateway_url` in `server/config.php`.
- Sign-in needs the PHP server. The Firebase option can still carry reports and locations but cannot sign people in.

## Not tested
This was written without an Android build environment, so it has not been compiled or run. Build it in Android Studio and send me any error lines.
