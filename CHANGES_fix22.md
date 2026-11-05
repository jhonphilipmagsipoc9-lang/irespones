# fix21 changes
1. Messages: citizens tap "New message" and choose any responder to write to (direct chat, shared online).
   Responders see these chats in Messages and can reply.
2. Calls: calls now use their own network threads, more STUN/TURN servers (UDP, TCP and TLS), faster polling,
   ICE retries, a fix for answering twice, and a 30-second limit so "Connecting" can never hang forever.
3. Login: shows a "Loading..." screen while signing in. The "Too many tries. Wait 5 minute(s)" lock is removed.
4. Buttons: labels stay on one line (text shrinks a little) so "Navigate" no longer breaks into "Navigat / e".

# fix22
Location turns on by itself when the app opens: the app asks for the location permission, then shows the phone one-tap "Turn on location?" box (Google Play services). No trip to Settings. Added play-services-location to app/build.gradle. If that box is declined, the existing "Turn on location" box appears as a backup.
