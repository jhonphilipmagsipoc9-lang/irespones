# Turning on the online service (about 5 minutes, free)

The app talks to a **Firebase Realtime Database** over plain HTTPS. No Firebase SDK and no `google-services.json` needed.

1. Go to https://console.firebase.google.com, **Add project** (any name; Analytics off is fine).
2. **Build > Realtime Database > Create database**. Pick a location near you (for example Singapore). Start in **test mode**.
3. Copy the database address shown at the top, like `https://myproject-default-rtdb.asia-southeast1.firebasedatabase.app`.
4. In the app on every phone: **Profile > Settings > Online service > Server address**, paste it, **Save**.
   (Or set `DEFAULT_CLOUD_URL` in `net/Config.java` before building.)
5. Both phones need internet. Sign in as a responder on one phone and a citizen on the other.

The status line on the citizen's Home screen turns green: "Online · free internet call ready".

## Rules (important)
Test mode lets anyone with the address read and write, and it **expires after 30 days**. That is fine for a demo, not
for real emergencies. For anything real, put the database behind Firebase Authentication and tighten the rules; the
app would then need to sign in to Firebase (a small change in `net/Cloud.java`).

## Calls
Calls use WebRTC (`io.getstream:stream-webrtc-android`). STUN servers are included. Some mobile networks need a
**TURN** relay: add yours in `call/CallConfig.java` (the public relay listed there is for testing only).
Incoming calls ring while the app is open or a responder is signed in (their location service keeps the app alive).
A phone that has fully closed the app cannot ring; that needs Firebase Cloud Messaging push, not included.

## What is stored online
`directory/` (who is online, responders' positions), `incidents/`, `messages/`, `alerts/`, and short-lived `calls/`
and `inbox/` entries. Photos and passwords are never uploaded.
