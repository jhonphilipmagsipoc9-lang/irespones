<?php
// Database settings. For XAMPP the defaults below work after you create the database (see SERVER_SETUP.md).
return [
    'driver' => 'mysql',            // 'mysql' (XAMPP / MariaDB / MySQL) or 'sqlite' (testing only)
    'host'   => '127.0.0.1',
    'port'   => 3306,
    'dbname' => 'iresponse',
    'user'   => 'root',
    'pass'   => '',
    'sqlite_file' => __DIR__ . '/iresponse.sqlite',
    // Optional shared secret. If not empty, every request must send ?auth=THIS_VALUE (the app does not send it yet).
    'secret' => '',
    // Moves accounts that only existed on a phone onto the server the first time their owner signs in. Set to false
    // once everyone has signed in once.
    'allow_migrate' => true,
    // Real SMS for password reset: a URL that sends one text when called with ?to=<number>&text=<message>.
    // Leave empty for testing: the app then shows the reset code on screen.
    'sms_gateway_url' => '',
];
