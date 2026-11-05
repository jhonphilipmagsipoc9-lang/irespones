# fix19: the fix13 map, with routes that follow the roads

Not compiled (no Android SDK). Build with `./gradlew assembleDebug` and send the first error line if it fails.

## What this is
- The map screens are back to the fix13 map: same pins, same search, same card, same OpenStreetMap tiles, same animated route line (dashed, coloured by status, bright dot with trail, pulse at the destination).
- Kept from later versions: profile pictures on responder pins (fix15), evacuation sites, station pictures. Nothing else changed.
- The map redesign (fix16 to fix18: clusters, solid blue line, filter, bigger tiles) is **removed** in this build. Those files are not in this zip.

## New: routes follow the roads
- Main map, incident tracker and station picker: the animated route now runs along the real streets (free OpenStreetMap OSRM service, the same one Directions already used) instead of a straight line to the station.
- Until the road route arrives, or with no internet, the old straight animated line is shown, then it is replaced.
- Each route is fetched once and kept. At most two requests run at a time.
- Driving routes only. The OSRM demo server is for light use and needs internet.

## Files changed vs fix15
`utils/MapHelper.java` (road route helpers), `fragments/MapFragment.java`, `utils/IncidentViews.java`, `activities/StationPickerActivity.java`. `RouteAnim.java` and `DirectionsActivity.java` are the original ones.
