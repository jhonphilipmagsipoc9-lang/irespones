# fix15: Stage A (theme) + Stage B (Home), SOS removed, profile pictures on the map

Not compiled (no Android SDK in the build sandbox). Run `./gradlew assembleDebug` and report any error.

## Removed
- The SOS button, its hold ring, countdown screen, "SOS goes to ..." chip and the `sos_service` setting.
  Deleted: `dialog_sos_countdown.xml`, `bg_sos_button.xml`, `bg_sos_halo.xml`, `SosRingView.java`, SOS strings, `Theme.RescueLink.Sos`.
- Home is the four service tiles again (Police, Fire, DRRMO, Medical), with the new 28dp corners, tiles that grow with large fonts, and darker subtitle text.
- Home order: header, your active emergency, alert, status line, tiles.
- Status line green is now `#15803D` (5.0:1 with white text; was 3.3:1).

## Kept from Stage A
Colour, dimen and style additions (severity colours, radii, tonal / interactive card, large buttons), `bg_hero_card_xl`, `card_stroke`.

## Added: profile pictures on the map
- `MapHelper.addAvatarMarker(map, lat, lon, title, ringColor, User)`: round pin with the person's profile picture,
  white border, a ring and a pointer. With no picture it shows a coloured disc with their first letter (same rule as the profile screens).
  Decoded pictures are cached (40 entries), so the 5-second responder refresh does not re-read files.
- `MapFragment` responder pins now use it (was the generic person icon). Ring: purple = live, grey = last seen; the title still says "(last seen here)".
- `IncidentViews` (the live tracker map) responder pin uses it too (was the service icon).
- Station and incident pins are unchanged.
