# iResponse fix12: what changed

## Sign-in no longer needs the server
- **Login, Create account, Forgot password, Change password, enable/disable/delete accounts, chat and the assistant now work on the phone itself.** No server address, no internet needed. The "Connect to the server" box is gone.
- Accounts are saved on the phone (passwords as salted hashes). 5 wrong passwords lock that number for 5 minutes.
- The optional online service (Settings > Server address) is still there for sharing reports and locations. Nothing about signing in depends on it.
- Removed: `AuthApi`, `AccountSync`, `ServerPrompt`. `server/auth.php` is no longer used.

## Continue with Google
- New **Continue with Google** button on the login screen (Credential Manager, the standard Google chooser).
- First time: the person completes the normal citizen details (mobile number, ID photo, face scan). No password. After that Google signs them in.
- Needs your own Google client ID: see **GOOGLE_LOGIN_SETUP.md**. Until then the button explains that it is not set up.

## Phone number sign-in and sign-up
- New **Continue with phone number** button: type the number, get a 6-digit SMS code, and you are in (citizens only).
- A number with no account offers **Create account** with the number filled in. Create account still asks for a password so there is always a way in.
- Password reset works the same way: the code goes to the number saved on the account.

## Important limits (please read)
1. **Accounts are per phone.** Without a server, an account made on one phone does not exist on another. A responder made on the admin's phone cannot sign in on the responder's own phone. If you need shared accounts, the PHP server route (fix11) is what does that.
2. **The SMS code is sent from the phone's own SIM** (there is no SMS service). The phone needs a SIM with SMS and some load. For sign-in and password reset the code is **never shown on screen**, because that would let anyone type another person's number and get in. Account creation still has the old on-screen test mode.
3. For real, production phone verification use an SMS gateway or Firebase Phone Auth.
4. The Google ID token is read on the phone and not re-verified by a server. Fine while accounts are local; verify it server-side if accounts ever move online.

## Not tested
Written without an Android build environment. Java syntax was checked, but it was not compiled against the Android SDK or run. Build it in Android Studio and send me any error lines. New dependencies: `androidx.credentials` 1.2.2, `credentials-play-services-auth` 1.2.2, `googleid` 1.1.1.
