-- ===========================================================================
-- InLearn — step 2: create the tables
--
-- Run against the application database, for example:
--     mysql -u root -p inlearn < database/02_schema.sql
--
-- Conventions
--   * Every DATETIME column stores UTC. The application converts to and from
--     the user's display time zone and opens each connection with
--     time_zone = '+00:00' so CURRENT_TIMESTAMP defaults are UTC too.
--   * Points use DECIMAL(7,2) so 9.5 / 10 is stored exactly (no floating point).
--   * version columns support optimistic locking: an UPDATE only succeeds if
--     the row still has the version the user originally loaded.
--   * Nothing cascades on delete. Academic records are preserved: users are
--     deactivated, courses are archived and enrollments are withdrawn instead
--     of being removed. Foreign keys use RESTRICT so a mistaken delete fails
--     loudly instead of silently removing history.
-- ===========================================================================

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    full_name         VARCHAR(120) NOT NULL,
    role              ENUM('ADMIN', 'INSTRUCTOR', 'STUDENT') NOT NULL,
    status            ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    theme_preference  ENUM('SYSTEM', 'LIGHT', 'DARK') NOT NULL DEFAULT 'SYSTEM',
    last_login_at     DATETIME     NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE courses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(20)  NOT NULL,
    title          VARCHAR(150) NOT NULL,
    description    TEXT         NULL,
    term           VARCHAR(40)  NOT NULL,
    instructor_id  BIGINT       NULL,
    status         ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    archived_at    DATETIME     NULL,
    version        INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_courses_code (code),
    KEY idx_courses_instructor (instructor_id),
    KEY idx_courses_status (status),
    CONSTRAINT fk_courses_instructor FOREIGN KEY (instructor_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE enrollments (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    course_id     BIGINT   NOT NULL,
    student_id    BIGINT   NOT NULL,
    status        ENUM('ACTIVE', 'WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    enrolled_at   DATETIME NOT NULL,
    withdrawn_at  DATETIME NULL,
    version       INT      NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One enrollment row per student per course. Withdrawing changes the status
    -- and re-enrolling reactivates the same row, so history is never duplicated.
    UNIQUE KEY uk_enrollments_course_student (course_id, student_id),
    KEY idx_enrollments_student_status (student_id, status),
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE course_materials (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    course_id          BIGINT        NOT NULL,
    title              VARCHAR(150)  NOT NULL,
    description        VARCHAR(1000) NULL,
    material_type      ENUM('FILE', 'LINK') NOT NULL,
    link_url           VARCHAR(2048) NULL,
    original_filename  VARCHAR(255)  NULL,
    storage_key        VARCHAR(120)  NULL,
    file_size_bytes    BIGINT        NULL,
    uploaded_by        BIGINT        NOT NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_materials_storage_key (storage_key),
    KEY idx_materials_course_created (course_id, created_at),
    CONSTRAINT fk_materials_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_materials_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE RESTRICT,
    -- A FILE material always has stored file metadata; a LINK always has a URL.
    CONSTRAINT chk_materials_type_fields CHECK (
        (material_type = 'FILE' AND storage_key IS NOT NULL AND original_filename IS NOT NULL AND link_url IS NULL)
        OR (material_type = 'LINK' AND link_url IS NOT NULL AND storage_key IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE assignments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    course_id       BIGINT       NOT NULL,
    title           VARCHAR(150) NOT NULL,
    instructions    TEXT         NOT NULL,
    due_at          DATETIME     NOT NULL,
    max_points      DECIMAL(7, 2) NOT NULL,
    late_policy     ENUM('NO_LATE', 'ALLOW_LATE') NOT NULL DEFAULT 'NO_LATE',
    late_cutoff_at  DATETIME     NULL,
    created_by      BIGINT       NOT NULL,
    version         INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_assignments_course_due (course_id, due_at),
    KEY idx_assignments_due (due_at),
    CONSTRAINT fk_assignments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignments_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_assignments_max_points CHECK (max_points > 0 AND max_points <= 1000),
    -- A late cutoff only makes sense when late work is accepted, and must follow the due date.
    CONSTRAINT chk_assignments_late_cutoff CHECK (
        late_cutoff_at IS NULL OR (late_policy = 'ALLOW_LATE' AND late_cutoff_at > due_at)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE submissions (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    assignment_id      BIGINT       NOT NULL,
    student_id         BIGINT       NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    storage_key        VARCHAR(120) NOT NULL,
    file_size_bytes    BIGINT       NOT NULL,
    submitted_at       DATETIME     NOT NULL,
    is_late            BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_number     INT          NOT NULL DEFAULT 1,
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One current submission per student per assignment. A replacement updates
    -- this row (and increments attempt_number) rather than adding another one.
    UNIQUE KEY uk_submissions_assignment_student (assignment_id, student_id),
    UNIQUE KEY uk_submissions_storage_key (storage_key),
    KEY idx_submissions_student (student_id),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_submissions_attempt CHECK (attempt_number >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE grades (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    submission_id  BIGINT        NOT NULL,
    points         DECIMAL(7, 2) NOT NULL,
    feedback       TEXT          NULL,
    graded_by      BIGINT        NOT NULL,
    graded_at      DATETIME      NOT NULL,
    version        INT           NOT NULL DEFAULT 0,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One grade per submission. The upper bound (points <= max_points) depends
    -- on the assignment row, so the service layer validates it.
    UNIQUE KEY uk_grades_submission (submission_id),
    KEY idx_grades_graded_at (graded_at),
    CONSTRAINT fk_grades_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_grades_graded_by FOREIGN KEY (graded_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_grades_points CHECK (points >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE announcements (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    course_id   BIGINT       NOT NULL,
    author_id   BIGINT       NOT NULL,
    title       VARCHAR(150) NOT NULL,
    body        TEXT         NOT NULL,
    version     INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_announcements_course_created (course_id, created_at),
    CONSTRAINT fk_announcements_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_announcements_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
