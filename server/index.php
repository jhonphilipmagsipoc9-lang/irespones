<?php
/**
 * iResponse shared server. Speaks the same tiny REST dialect the app already uses for Firebase
 * (GET / PUT / POST / PATCH / DELETE on  <base>/<path>.json ) but stores everything in a SQL table.
 * Works with MySQL / MariaDB (XAMPP) and, for testing, SQLite. No framework needed.
 */
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$cfg = require __DIR__ . '/config.php';

function fail(int $code, string $msg): void {
    http_response_code($code);
    echo json_encode(['error' => $msg]);
    exit;
}

try {
    if ($cfg['driver'] === 'sqlite') {
        $db = new PDO('sqlite:' . $cfg['sqlite_file']);
        $create = 'CREATE TABLE IF NOT EXISTS nodes (path TEXT PRIMARY KEY, val TEXT NOT NULL, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)';
    } else {
        $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $cfg['host'], $cfg['port'], $cfg['dbname']);
        $db = new PDO($dsn, $cfg['user'], $cfg['pass']);
        $create = 'CREATE TABLE IF NOT EXISTS nodes (
            path VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
            val MEDIUMTEXT NOT NULL,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (path)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4';
    }
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->exec($create);
} catch (Throwable $e) {
    fail(500, 'database: ' . $e->getMessage());
}

if ($cfg['secret'] !== '' && ($_GET['auth'] ?? '') !== $cfg['secret']) fail(401, 'bad secret');

// ---- which path was asked for? -------------------------------------------------------------------------
$uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
if (isset($_SERVER['PATH_INFO']) && $_SERVER['PATH_INFO'] !== '') {
    $p = $_SERVER['PATH_INFO'];
} else {
    $script = $_SERVER['SCRIPT_NAME'] ?? '';
    $p = str_starts_with($uri, $script) ? substr($uri, strlen($script)) : $uri;
}
$p = rawurldecode($p);
$p = preg_replace('/\.json$/', '', trim($p, '/'));
$p = trim((string)$p, '/');
if ($p !== '' && !preg_match('#^[A-Za-z0-9_\-/]+$#', $p)) fail(400, 'bad path');
if (strlen($p) > 170) fail(400, 'path too long');

$method = $_SERVER['REQUEST_METHOD'];

// ---- accounts: sign-in, create account and password reset (see auth.php) -------------------------------
if (strncmp($p, 'auth/', 5) === 0) {
    if ($method !== 'POST') fail(405, 'POST only');
    require __DIR__ . '/auth.php';
    $b = json_decode((string)file_get_contents('php://input'), true);
    try { auth_handle($db, $cfg, substr($p, 5), is_array($b) ? $b : []); }
    catch (Throwable $e) { fail(500, 'auth: ' . $e->getMessage()); }
    exit;
}
$silent = ($_GET['print'] ?? '') === 'silent';
$bodyRaw = file_get_contents('php://input');

// ---- helpers -------------------------------------------------------------------------------------------
/** Deletes the node and everything below it. */
function wipe(PDO $db, string $p): void {
    if ($p === '') { $db->exec('DELETE FROM nodes'); return; }
    $st = $db->prepare('DELETE FROM nodes WHERE path = ? OR (path >= ? AND path < ?)');
    $st->execute([$p, $p . '/', $p . '0']);   // '0' is the character right after '/', so this is "everything under p/"
}

/** Writes a JSON value as one row per leaf. */
function store(PDO $db, string $p, $v): void {
    if ($v === null) return;
    // Firebase "server value": {".sv":"timestamp"} means "the server's clock right now, in milliseconds".
    if (is_array($v) && count($v) === 1 && ($v['.sv'] ?? null) === 'timestamp') $v = (int)round(microtime(true) * 1000);
    if (is_array($v) || is_object($v)) {
        foreach ((array)$v as $k => $child) {
            $k = (string)$k;
            if ($k === '' || !preg_match('#^[A-Za-z0-9_\-]+$#', $k)) continue;
            store($db, ($p === '' ? '' : $p . '/') . $k, $child);
        }
        return;
    }
    $st = $db->prepare('REPLACE INTO nodes (path, val) VALUES (?, ?)');
    $st->execute([$p, json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES)]);
}

/** Reads the node and everything below it back as nested JSON. */
function load(PDO $db, string $p) {
    if ($p === '') {
        $rows = $db->query('SELECT path, val FROM nodes')->fetchAll(PDO::FETCH_NUM);
    } else {
        $st = $db->prepare('SELECT path, val FROM nodes WHERE path = ? OR (path >= ? AND path < ?)');
        $st->execute([$p, $p . '/', $p . '0']);
        $rows = $st->fetchAll(PDO::FETCH_NUM);
    }
    if (!$rows) return null;
    $root = null;
    $cut = $p === '' ? 0 : strlen($p) + 1;
    foreach ($rows as [$path, $val]) {
        $v = json_decode($val, true);
        if ($p !== '' && $path === $p) return $v;
        $parts = explode('/', substr($path, $cut));
        if ($root === null) $root = [];
        $ref = &$root;
        foreach ($parts as $i => $part) {
            if ($i === count($parts) - 1) { $ref[$part] = $v; }
            else { if (!isset($ref[$part]) || !is_array($ref[$part])) $ref[$part] = []; $ref = &$ref[$part]; }
        }
        unset($ref);
    }
    return $root;
}

function pushKey(): string {
    static $chars = '-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz';
    $now = (int)(microtime(true) * 1000);
    $s = '';
    for ($i = 0; $i < 8; $i++) { $s = $chars[$now % 64] . $s; $now = intdiv($now, 64); }
    for ($i = 0; $i < 12; $i++) $s .= $chars[random_int(0, 63)];
    return $s;
}

// ---- do it ---------------------------------------------------------------------------------------------
try {
    switch ($method) {
        case 'GET':
            echo json_encode(load($db, $p), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION);
            break;

        case 'PUT':
        case 'PATCH':
        case 'POST':
            $data = json_decode($bodyRaw, true);
            if ($bodyRaw !== '' && $data === null && strtolower(trim($bodyRaw)) !== 'null') fail(400, 'bad json');
            $db->beginTransaction();
            if ($method === 'POST') {
                $key = pushKey();
                $p = ($p === '' ? '' : $p . '/') . $key;
                store($db, $p, $data);
                $db->commit();
                if ($silent) { http_response_code(204); break; }
                echo json_encode(['name' => $key]);
                break;
            }
            if ($method === 'PUT') {
                wipe($db, $p);
                store($db, $p, $data);
            } else { // PATCH: replace only the keys that were sent
                if (!is_array($data)) { $db->rollBack(); fail(400, 'PATCH needs an object'); }
                foreach ($data as $k => $child) {
                    $kp = ($p === '' ? '' : $p . '/') . $k;
                    wipe($db, $kp);
                    store($db, $kp, $child);
                }
            }
            $db->commit();
            if ($silent) { http_response_code(204); break; }
            echo $bodyRaw === '' ? 'null' : json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            break;

        case 'DELETE':
            wipe($db, $p);
            if ($silent) { http_response_code(204); break; }
            echo 'null';
            break;

        case 'OPTIONS':
            http_response_code(204);
            break;

        default:
            fail(405, 'method not allowed');
    }
} catch (Throwable $e) {
    if ($db->inTransaction()) $db->rollBack();
    fail(500, $e->getMessage());
}
