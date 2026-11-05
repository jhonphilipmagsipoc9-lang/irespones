-- iResponse shared database (MySQL / MariaDB). index.php also creates this table by itself on first request.
CREATE DATABASE IF NOT EXISTS iresponse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE iresponse;

-- One row per value. `path` is the location, like  incidents/INC-1001/status  and `val` is the JSON value.
CREATE TABLE IF NOT EXISTS nodes (
    path       VARCHAR(190) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    val        MEDIUMTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Handy views for looking at the data in phpMyAdmin
CREATE OR REPLACE VIEW v_incidents AS
    SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(path,'/',2),'/',-1) AS incident_id,
           SUBSTRING(path, LENGTH(SUBSTRING_INDEX(path,'/',2))+2) AS field, val, updated_at
    FROM nodes WHERE path LIKE 'incidents/%';
CREATE OR REPLACE VIEW v_online AS
    SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(path,'/',2),'/',-1) AS username,
           SUBSTRING(path, LENGTH(SUBSTRING_INDEX(path,'/',2))+2) AS field, val, updated_at
    FROM nodes WHERE path LIKE 'directory/%';
