# RescueLink UI/UX redesign - Stage 1 (foundation + global motion + key screens)

NOT yet compiled: the build sandbox had no Android SDK or network. XML well-formedness and every @resource / R.x
reference were checked with a script; Java was reviewed by hand. Run `./gradlew assembleDebug` and report any error.

## Design system
- values/colors.xml: navy primary (#0A1F44), professional blue secondary (#2563EB), red emergency, green success,
  amber warning, light-gray background, white surfaces. All old color names kept so nothing breaks.
- values/styles.xml: Material 3 theme; type scale (TextAppearance.RL.*); one card, button (primary, tonal, outline,
  emergency, text, danger), input, dropdown, badge, search style; dialog and bottom-sheet shapes.
- values/dimens.xml: 4dp spacing scale, radii, touch targets. values/ids.xml: motion tags.
- Status colors (IncidentStatus, UiUtils) moved to the same palette.

## Motion (res/anim + utils/UiAnim.java)
- Screen transitions (slide + fade) for every activity via the theme; short fade between bottom-nav tabs.
- Every screen: staggered content entrance, press-scale on buttons and clickable cards, animated list rows.
  Installed once in RescueLinkApp (no per-screen code). Only alpha/translation/scale; taps are never delayed.
- Dialog scale/fade, count-up dashboard numbers, animated bars, pulsing "ready" dot on citizen Home, bell shake on new alert.

## Screens touched
Login (brand hero), Citizen Home (hero header), Responder/Admin dashboards (hero header), all 14 back buttons,
list empty states, nav bar, status badges. Every other screen picks up the new theme, buttons, inputs, cards.

## Stage 2 - Profile and Settings (layout inspired by a Dribbble "Profile Page"; our own colors)
- Profile: round avatar with edit badge, name, role, and a status chip (duty status for responders, Active/Disabled otherwise).
- Grouped rows with icon discs and chevrons: Personal info, Account (Edit profile, Emergency contacts, Notifications),
  General (Settings, About), and a red Log out row (still asks to confirm).
- New Settings screen: emergency call method (Auto/Phone/SMS), Reduce motion switch, shortcut to system app permissions.
- New utils/MenuUi.java builds these rows; reuse it for any other list-style screen.
