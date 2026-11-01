# iResponse (Android, Java + XML)

A 911-style emergency app. By default it works completely on its own on one phone. Add a free **online service**
(see `FIREBASE_SETUP.md`, about 5 minutes) and the phones talk to each other: responders' live locations appear on the
Command Center map, reports reach the closest responder's phone, and calls are free **internet voice calls** that need
no load.

Four big buttons: **Police, Fire, DRRMO, Hospital**. One tap starts the help.

## How a call works (citizen)
| Phone has | What one tap does |
|---|---|
| Wi-Fi or data + online service | **Free internet call** (like Messenger) to the closest responder who is online. A report is also sent. |
| SIM with load | **Phone call** straight to the nearest station's saved number (falls back to 911). |
| SIM, message only | **SMS** with name and a location link sent to the station. |
| No SIM | The app says so and tells the person to find a signal or ask someone to call 911. |

Auto tries the internet call first and falls back to the SIM when nobody is online. The person can pin **Auto / Internet / Phone / SMS** on the Home screen. A signed-in citizen's emergency is also saved as a
critical report (location + Medical ID, routed to the nearest station of that service) in their report history.
The sign-in screen has four emergency buttons that work without an account.

## First start
The first time the app opens it creates:
- the Command Center account: username `100100`, password `admin123` (the sign-in screen shows this until you change
  the password in **Profile > Edit**), and
- four starter stations in Bayawan City (Hospital, Police, Fire, DRRMO). Edit them or add your own under
  **Manage > Stations**, and give each a phone number.

## Demo accounts (testing only)
Created automatically on every phone. Sign in with the number and password `123456` (Command Center: `100100` / `admin123`).

| Role | Sign-in number | Name |
|---|---|---|
| Citizen | `09170000001`, `09170000010` | Juan Dela Cruz, Maria Santos |
| Police responder | `200001`, `200005` | Officer Police, Officer Police 2 |
| Fire responder | `200002`, `200006` | Firefighter Fire, Firefighter Fire 2 |
| DRRMO responder | `200003`, `200007` | Responder DRRMO, Responder DRRMO 2 |
| Medical responder | `200004`, `200008` | Medic Medical, Medic Medical 2 |
| Service admins | `100001` Police, `100002` Fire, `100003` DRRMO, `100004` Medical | |

Delete these before real use.

## Live tracking and "closest responder"
With the online service on, every signed-in responder's phone shares its GPS position every few seconds, even in the
background. The Command Center (and every other screen with the Map) sees the pins move, marked **Live now** or
**Offline**. When a citizen sends an emergency, the closest **online** responder of that service is put in charge and
their phone rings first; the rest of the service rings only if nobody sets out within a minute.

## Command Center (admin)
- **Create account**: responder (choose their station), another admin, or a citizen. Manage > Users > Create.
- **Stations**: add or edit Police, Fire, DRRMO and Hospital stations: name, emergency number, map position, availability.
- Control center, incidents, broadcasts, enable/disable/delete accounts, activity log.
- Citizens can also sign up themselves from the sign-in screen.

## One phone = one database, unless the online service is on
Without the online service, phones do not talk to each other. With it, these are shared: who is online, responder
positions, reports, report chat and broadcasts. **Accounts are not shared** (passwords never leave the phone), so use
the demo accounts, or create each account on the phone where that person will sign in.

## Privacy
Passwords are stored only as salted hashes and never leave the phone. The admin cannot see or reset passwords.

## Home-screen widget
Long-press the home screen > **Widgets** > **iResponse SOS**. It adds a strip with Police, Fire, DRRMO and Medical buttons.
A tap asks "Call now?" first so a pocket tap cannot call by accident. To make it a one-tap call, set
`CONFIRM_FIRST = false` in `widget/WidgetActionActivity.java`. For the call to go straight through (instead of opening
the dialer with the number filled in), open the app once and allow the phone permission.

## Lightweight build
The release build enables R8 code shrinking/optimization and unused-resource removal to reduce the installed APK size. The debug build remains unshrunk for easier testing. Map tiles still require internet; the app's core features remain local/offline.

## Build and run
1. Android Studio > Open > this folder, let Gradle sync (JDK 17, Android Studio Ladybug or newer). Run on a phone or emulator (Android 7.0+).
   From a terminal use `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).
   The app id is `com.irespons.redesign` (the pre-redesign app was `com.irespons.app`; the two install side by side); the Java package is still `com.rescuelink.app`, which is fine.
2. Sign in as `admin` / `admin123`, change the password, add phone numbers to the stations, create responder accounts.

## Notes
- Maps need internet for tiles (OpenStreetMap through osmdroid). Everything else works offline.
- Photos attached to reports stay on the phone.
- Updating from the old server version: accounts that were downloaded from the server cannot be used (their passwords
  were never on the phone) and are dropped on first start; reports, stations and messages are kept.
