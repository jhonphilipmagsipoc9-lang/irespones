# fix13: online sign-in with Firebase
- Why fix12 logged in offline: it deliberately moved sign-in onto the phone (local salted-hash check), no network.
- Now: number + password must also pass Firebase Authentication (REST, no SDK): `net/FirebaseAuth.java`.
- No internet = no sign-in. Phone-code sign-in also needs internet. Register / admin-created accounts / change password / forgot password all update Firebase.
- Setup: Web API key in strings.xml (`firebase_api_key`) + enable Email/Password. See FIREBASE_SETUP.md.
- Not compiled here (no Android SDK).
