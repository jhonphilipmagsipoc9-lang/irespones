> **fix12:** signing in, creating accounts and resetting passwords no longer use this server. It is now optional, only for sharing reports and locations between phones.

# Shared SQL database (MySQL) for iResponse

The app talks to a small PHP server (`server/index.php`) that stores everything in a **MySQL / MariaDB** table.
All phones then share online status, responder positions, reports, report chat, broadcasts and calls.
(Same as the Firebase option, but your own database.)

## A. Run it on your Windows PC with XAMPP (about 10 minutes, free)
1. Install **XAMPP** (apachefriends.org). Open the XAMPP Control Panel and **Start** both **Apache** and **MySQL**.
2. Copy the whole `server` folder to `C:\xampp\htdocs\` and rename it to `iresponse`.
   You now have `C:\xampp\htdocs\iresponse\index.php`.
3. Create the database: open http://localhost/phpmyadmin, click **Import**, choose `server/schema.sql`, click **Import**.
   (The table is also created automatically on the first request.)
4. Test in the PC browser: http://localhost/iresponse/index.php/directory.json should show `null`.
5. Find the PC's address: open Command Prompt, type `ipconfig`, note **IPv4 Address** (like `192.168.1.5`).
   Test from the phone browser (same Wi-Fi): `http://192.168.1.5/iresponse/index.php/directory.json` shows `null`.
   If it does not load, allow Apache in Windows Firewall (Private network) when Windows asks.
6. In the app on every phone: **Profile > Settings > Online service > Server address**, enter
   `http://192.168.1.5/iresponse/index.php` (your own IP) and **Save**.

The citizen's Home screen turns green when the server is reachable.

## B. Real hosting
Any PHP hosting with MySQL works. Upload the `server` folder, edit `config.php` (host, dbname, user, pass), import
`schema.sql`, and use `https://your-site.example/iresponse/index.php` as the server address. Use HTTPS for anything real.

## What is in the database
One table, `nodes` (`path`, `val`, `updated_at`): each row is one value, for example
`incidents/INC-1001/status` = `"Assigned"`. Open it in phpMyAdmin to see live data. Views `v_incidents` and
`v_online` show reports and who is online.

## Notes
- No passwords are uploaded (accounts stay on each phone, same as before).
- Open server: anyone who knows the address can read/write. Fine for a demo on your own network; for real use set
  `secret` in `config.php`, restrict access (firewall / HTTPS) and keep backups.
- Voice calls still need a TURN relay on some mobile networks (see FIREBASE_SETUP.md, Calls).

## Working offline
- The app opens and works with no internet: accounts, reports, stations, contacts and calls by SIM all use the data
  saved on the phone. Reports made offline are sent to the server automatically when the connection returns.
- Maps need internet the first time an area is viewed. Tiles already seen are kept on the phone for 30 days (up to
  about 400 MB), so places you have looked at still show offline.
- A responder's dashboard shows an "Offline" notice while the server cannot be reached.
