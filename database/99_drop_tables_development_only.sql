-- ===========================================================================
-- InLearn — DEVELOPMENT ONLY: remove every InLearn table
--
-- Deletes all data. Useful for starting over on a development machine:
--     mysql -u root -p inlearn < database/99_drop_tables_development_only.sql
--     mysql -u root -p inlearn < database/02_schema.sql
--
-- The integration tests run this script against the *_test database before
-- each test class. Tables are dropped children-first so foreign keys never block.
-- ===========================================================================

DROP TABLE IF EXISTS grades;
DROP TABLE IF EXISTS submissions;
DROP TABLE IF EXISTS assignments;
DROP TABLE IF EXISTS announcements;
DROP TABLE IF EXISTS course_materials;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS users;
