-- ===========================================================================
-- InLearn — step 1: create the databases and accounts
--
-- Run as a MySQL administrator, for example:
--     mysql -u root -p < database/01_create_database.sql
--
-- BEFORE RUNNING: replace both CHANGE_ME placeholders with strong passwords and
-- put the same values in config/application.properties and
-- config/test-database.properties. Never commit real passwords.
-- ===========================================================================

-- Application database. utf8mb4 stores every Unicode character, and the
-- accent/case-insensitive collation makes the unique email constraint
-- case-insensitive as well.
CREATE DATABASE IF NOT EXISTS inlearn
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- The desktop application only needs to read and write rows, never to change
-- the schema, so it gets data privileges only.
CREATE USER IF NOT EXISTS 'inlearn_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE, DELETE ON inlearn.* TO 'inlearn_app'@'localhost';

-- Isolated database for the JDBC integration tests. The tests drop and
-- recreate tables, so this account needs full rights on the test schema only.
CREATE DATABASE IF NOT EXISTS inlearn_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'inlearn_test'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON inlearn_test.* TO 'inlearn_test'@'localhost';

FLUSH PRIVILEGES;
