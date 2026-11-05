<?php
/**
 * iResponse accounts. Sign-in, account creation and password reset only happen here, on the server, so passwords
 * are checked and stored in one safe place (bcrypt) instead of on every phone.
 * Called from index.php for  POST <base>/auth/<action>.json  with a JSON body. Always answers {"ok":true,...}
 * or {"ok":false,"error":"..."} with HTTP 200 so the app can read the reason.
 */
declare(strict_types=1);

function auth_out(array $a): void { echo json_encode($a, JSON_UNESCAPED_UNICODE); exit; }
function auth_err(string $code): void { auth_out(['ok' => false, 'error' => $code]); }

function auth_setup(PDO $db, array $cfg): void {
    $db->exec('CREATE TABLE IF NOT EXISTS accounts (
        username VARCHAR(40) NOT NULL PRIMARY KEY,
        role VARCHAR(12) NOT NULL,
        name VARCHAR(120) NOT NULL,
        age INT NOT NULL DEFAULT 18,
        address VARCHAR(255) NOT NULL DEFAULT \'\',
        contact VARCHAR(40) NOT NULL DEFAULT \'\',
        service VARCHAR(20) NOT NULL DEFAULT \'\',
        station VARCHAR(120) NOT NULL DEFAULT \'\',
        active INT NOT NULL DEFAULT 1,
        pw_hash VARCHAR(255) NOT NULL,
        salt VARCHAR(64) NOT NULL DEFAULT \'\',
        token VARCHAR(64) NOT NULL DEFAULT \'\',
        fails INT NOT NULL DEFAULT 0,
        locked_until BIGINT NOT NULL DEFAULT 0,
        reset_code VARCHAR(64) NOT NULL DEFAULT \'\',
        reset_exp BIGINT NOT NULL DEFAULT 0,
        reset_tries INT NOT NULL DEFAULT 0,
        created BIGINT NOT NULL DEFAULT 0)');
    $n = (int)$db->query('SELECT COUNT(*) FROM accounts')->fetchColumn();
    if ($n === 0) {   // first start: the Command Center account (change its password after the first sign-in)
        $st = $db->prepare('INSERT INTO accounts (username, role, name, age, address, pw_hash, created) VALUES (?,?,?,?,?,?,?)');
        $st->execute(['100100', 'Admin', 'Command Center', 30, 'Command Center', password_hash('admin123', PASSWORD_DEFAULT), now_ms()]);
    }
}

function now_ms(): int { return (int)round(microtime(true) * 1000); }

function acc_find(PDO $db, string $typed): ?array {
    $st = $db->prepare('SELECT * FROM accounts WHERE username = ?');
    $st->execute([$typed]);
    $r = $st->fetch(PDO::FETCH_ASSOC);
    if ($r) return $r;
    if ($typed === '') return null;
    $st = $db->prepare('SELECT * FROM accounts WHERE contact = ?');
    $st->execute([$typed]);
    $r = $st->fetch(PDO::FETCH_ASSOC);
    return $r ?: null;
}

function acc_public(array $r): array {
    return ['username' => $r['username'], 'role' => $r['role'], 'name' => $r['name'], 'age' => (int)$r['age'],
        'address' => $r['address'], 'contact' => $r['contact'], 'service' => $r['service'],
        'station' => $r['station'], 'active' => (int)$r['active'] === 1, 'created' => (int)$r['created']];
}

function acc_by_token(PDO $db, string $token): ?array {
    if (strlen($token) < 20) return null;
    $st = $db->prepare('SELECT * FROM accounts WHERE token = ?');
    $st->execute([$token]);
    $r = $st->fetch(PDO::FETCH_ASSOC);
    return ($r && (int)$r['active'] === 1) ? $r : null;
}

function acc_pw_ok(array $r, string $pw): bool {
    if ($r['salt'] !== '') return hash_equals($r['pw_hash'], hash('sha256', $r['salt'] . ':' . $pw));   // older phone-made account
    return password_verify($pw, $r['pw_hash']);
}

function acc_new_token(PDO $db, string $username): string {
    $t = bin2hex(random_bytes(24));
    $db->prepare('UPDATE accounts SET token = ?, fails = 0, locked_until = 0 WHERE username = ?')->execute([$t, $username]);
    return $t;
}

function s(array $b, string $k, int $max = 120): string {
    $v = $b[$k] ?? '';
    return is_string($v) ? mb_substr(trim($v), 0, $max) : '';
}

function auth_handle(PDO $db, array $cfg, string $action, array $b): void {
    auth_setup($db, $cfg);
    $now = now_ms();
    $services = ['Police', 'Fire', 'DRRMO', 'Medical'];

    switch ($action) {
    case 'login': {
        $r = acc_find($db, s($b, 'username', 40));
        $pw = is_string($b['password'] ?? null) ? $b['password'] : '';
        if (!$r) auth_err('invalid');
        if ((int)$r['locked_until'] > $now) auth_err('locked');
        if (!acc_pw_ok($r, $pw)) {
            $f = (int)$r['fails'] + 1;
            $lock = $f >= 8 ? $now + 5 * 60 * 1000 : 0;
            $db->prepare('UPDATE accounts SET fails = ?, locked_until = ? WHERE username = ?')->execute([$lock ? 0 : $f, $lock, $r['username']]);
            auth_err('invalid');
        }
        if ((int)$r['active'] !== 1) auth_err('disabled');
        if ($r['salt'] !== '') {   // upgrade an older hash to bcrypt now that the password is known
            $db->prepare('UPDATE accounts SET pw_hash = ?, salt = \'\' WHERE username = ?')->execute([password_hash($pw, PASSWORD_DEFAULT), $r['username']]);
        }
        auth_out(['ok' => true, 'token' => acc_new_token($db, $r['username']), 'user' => acc_public($r)]);
    }

    case 'register': {   // citizens only
        $u = preg_replace('/\D/', '', s($b, 'username', 20));
        $pw = is_string($b['password'] ?? null) ? $b['password'] : '';
        $name = s($b, 'name'); $age = (int)($b['age'] ?? 0);
        if (!preg_match('/^09\d{9}$/', (string)$u)) auth_err('bad_number');
        if ($name === '' || $age < 1 || $age > 120 || strlen($pw) < 6) auth_err('bad_input');
        if (acc_find($db, $u)) auth_err('exists');
        $db->prepare('INSERT INTO accounts (username, role, name, age, address, contact, pw_hash, created) VALUES (?,?,?,?,?,?,?,?)')
           ->execute([$u, 'Citizen', $name, $age, s($b, 'address', 255), $u, password_hash($pw, PASSWORD_DEFAULT), $now]);
        $r = acc_find($db, $u);
        auth_out(['ok' => true, 'token' => acc_new_token($db, $u), 'user' => acc_public($r)]);
    }

    case 'admin_create': {
        $me = acc_by_token($db, s($b, 'token', 64));
        if (!$me) auth_err('expired');
        if ($me['role'] !== 'Admin') auth_err('not_allowed');
        $role = s($b, 'role', 12); $service = s($b, 'service', 20);
        $u = preg_replace('/\D/', '', s($b, 'username', 20));
        $pw = is_string($b['password'] ?? null) ? $b['password'] : '';
        $name = s($b, 'name');
        if ($name === '' || strlen($pw) < 6 || !in_array($service, $services, true)) auth_err('bad_input');
        $commandCenter = $me['service'] === '';
        if ($commandCenter) { if ($role !== 'Admin') auth_err('not_allowed'); }
        else { if ($role !== 'Responder' || $service !== $me['service']) auth_err('not_allowed'); }
        if ($role === 'Responder' && !preg_match('/^09\d{9}$/', (string)$u)) auth_err('bad_number');
        if ($role === 'Admin' && !preg_match('/^\d{4,10}$/', (string)$u)) auth_err('bad_number');
        if (acc_find($db, (string)$u)) auth_err('exists');
        $db->prepare('INSERT INTO accounts (username, role, name, age, address, contact, service, station, pw_hash, created) VALUES (?,?,?,?,?,?,?,?,?,?)')
           ->execute([$u, $role, $name, 30, s($b, 'address', 255), s($b, 'contact', 40), $service, s($b, 'station'), password_hash($pw, PASSWORD_DEFAULT), $now]);
        auth_out(['ok' => true, 'user' => acc_public(acc_find($db, (string)$u))]);
    }

    case 'admin_set': {
        $me = acc_by_token($db, s($b, 'token', 64));
        if (!$me) auth_err('expired');
        if ($me['role'] !== 'Admin') auth_err('not_allowed');
        $t = acc_find($db, s($b, 'username', 40));
        if (!$t) auth_err('not_found');
        if ($t['username'] === $me['username']) auth_err('not_allowed');
        $ok = $me['service'] === '' ? true : ($t['role'] === 'Responder' && $t['service'] === $me['service']);
        if (!$ok) auth_err('not_allowed');
        $a = s($b, 'action', 10);
        if ($a === 'delete') $db->prepare('DELETE FROM accounts WHERE username = ?')->execute([$t['username']]);
        elseif ($a === 'enable' || $a === 'disable')
            $db->prepare('UPDATE accounts SET active = ?, token = \'\' WHERE username = ?')->execute([$a === 'enable' ? 1 : 0, $t['username']]);
        else auth_err('bad_input');
        auth_out(['ok' => true]);
    }

    case 'check': {   // is this saved sign-in still allowed? (used when the phone comes back online)
        $me = acc_by_token($db, s($b, 'token', 64));
        auth_out($me ? ['ok' => true, 'user' => acc_public($me)] : ['ok' => false, 'error' => 'expired']);
    }

    case 'logout': {
        $db->prepare('UPDATE accounts SET token = \'\' WHERE token = ? AND token <> \'\'')->execute([s($b, 'token', 64)]);
        auth_out(['ok' => true]);
    }

    case 'change_password': {
        $me = acc_by_token($db, s($b, 'token', 64));
        $cur = is_string($b['current'] ?? null) ? $b['current'] : '';
        $new = is_string($b['password'] ?? null) ? $b['password'] : '';
        if (!$me) auth_err('expired');
        if (strlen($new) < 6) auth_err('bad_input');
        if (!acc_pw_ok($me, $cur)) auth_err('invalid');
        $db->prepare('UPDATE accounts SET pw_hash = ?, salt = \'\' WHERE username = ?')->execute([password_hash($new, PASSWORD_DEFAULT), $me['username']]);
        auth_out(['ok' => true]);
    }

    case 'reset_request': {
        $r = acc_find($db, preg_replace('/\D/', '', s($b, 'username', 20)) ?: '');
        if (!$r || (int)$r['active'] !== 1) auth_err('not_found');
        $to = preg_match('/^09\d{9}$/', $r['contact']) ? $r['contact'] : (preg_match('/^09\d{9}$/', $r['username']) ? $r['username'] : '');
        if ($to === '') auth_err('no_mobile');
        $code = (string)random_int(100000, 999999);
        $db->prepare('UPDATE accounts SET reset_code = ?, reset_exp = ?, reset_tries = 0 WHERE username = ?')
           ->execute([hash('sha256', $code), $now + 10 * 60 * 1000, $r['username']]);
        $out = ['ok' => true, 'mobile' => $to];
        if (!empty($cfg['sms_gateway_url'])) {   // real SMS: the gateway URL gets  to=<number>&text=<message>
            @file_get_contents($cfg['sms_gateway_url'] . (strpos($cfg['sms_gateway_url'], '?') === false ? '?' : '&')
                . http_build_query(['to' => $to, 'text' => "iResponse code: $code"]));
        } else {
            $out['test_code'] = $code;   // no SMS gateway set up: the app shows the code on screen (testing only)
        }
        auth_out($out);
    }

    case 'reset_confirm': {
        $r = acc_find($db, preg_replace('/\D/', '', s($b, 'username', 20)) ?: '');
        $code = s($b, 'code', 10);
        $new = is_string($b['password'] ?? null) ? $b['password'] : '';
        if (!$r || $r['reset_code'] === '') auth_err('not_found');
        if ((int)$r['reset_exp'] < $now) auth_err('expired');
        if ((int)$r['reset_tries'] >= 5) auth_err('too_many');
        if (strlen($new) < 6) auth_err('bad_input');
        if (!hash_equals($r['reset_code'], hash('sha256', $code))) {
            $db->prepare('UPDATE accounts SET reset_tries = reset_tries + 1 WHERE username = ?')->execute([$r['username']]);
            auth_err('wrong_code');
        }
        $db->prepare('UPDATE accounts SET pw_hash = ?, salt = \'\', reset_code = \'\', reset_exp = 0, token = \'\' WHERE username = ?')
           ->execute([password_hash($new, PASSWORD_DEFAULT), $r['username']]);
        auth_out(['ok' => true]);
    }

    case 'migrate': {   // one-time move of an account that only existed on a phone; turn off with allow_migrate=false
        if (empty($cfg['allow_migrate'])) auth_err('not_allowed');
        $u = s($b, 'username', 40); $salt = s($b, 'salt', 64); $hash = s($b, 'hash', 64);
        $role = s($b, 'role', 12);
        if ($u === '' || $salt === '' || $hash === '' || !in_array($role, ['Citizen', 'Responder', 'Admin'], true)) auth_err('bad_input');
        if (acc_find($db, $u)) auth_err('exists');
        $db->prepare('INSERT INTO accounts (username, role, name, age, address, contact, service, station, pw_hash, salt, created) VALUES (?,?,?,?,?,?,?,?,?,?,?)')
           ->execute([$u, $role, s($b, 'name') ?: $u, max(1, (int)($b['age'] ?? 18)), s($b, 'address', 255), s($b, 'contact', 40),
               s($b, 'service', 20), s($b, 'station'), $hash, $salt, $now]);
        auth_out(['ok' => true]);
    }

    default:
        auth_err('unknown_action');
    }
}
