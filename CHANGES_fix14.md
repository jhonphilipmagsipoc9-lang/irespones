# fix14
- Login screen: only Sign in and Create account. "Continue with Google" and "Continue with phone number" removed.
- Settings: "Server address" removed. The address is set once in Config.DEFAULT_CLOUD_URL (see FIREBASE_SETUP.md).
- Theme color is now saved per account. Changing it only changes that account.
- Station pictures: admins and service admins can add a picture in the station form. It shows in the Stations list and the station card for everyone (shared through the online database, like profile pictures: data/StationPhotos.java).
- Evacuation sites: the DRRMO admin and the Command Center get Manage > Evacuation sites: add a site with name, map location, optional contact, open/closed and a picture. Citizens see them on the Map (Evacuation sites filter) and in the Stations list (Evacuation filter).
- Profile pictures of citizens, responders and admins were already shared through the online database; that needs DEFAULT_CLOUD_URL.
- Not compiled here (no Android SDK).
