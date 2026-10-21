# InLearn — Learning Management System

[![License: MIT](docs/badges/license-mit.svg)](LICENSE)
![Java 25](docs/badges/java-25.svg)
![JavaFX 25](docs/badges/javafx-25.svg)
![MySQL 8.4](docs/badges/mysql-8.4.svg)
![Windows, macOS and Linux](docs/badges/platforms.svg)

InLearn is a JavaFX desktop learning management system for colleges and universities. Administrators manage accounts, courses and enrollments; instructors share materials, post announcements, create assignments and grade submissions; students submit work and follow their grades — all stored in MySQL.

**Author:** Swayam Shakya ([@RippedPaper](https://github.com/RippedPaper)) · **License:** [MIT](LICENSE)

![Instructor dashboard](docs/screenshots/06-instructor-dashboard.png)

## Features

- **Secure sign-in** — bcrypt password hashing, generic error messages, temporary lockout after repeated failed attempts, optional "remember my email on this computer".
- **Three roles** — administrator, instructor and student, each with its own dashboard and navigation; every permission is enforced in the service layer.
- **Administration** — create, edit, search, deactivate and safely delete users; create, edit, archive and restore courses; assign instructors; enroll and withdraw students in batches.
- **Course workspaces** — overview, roster, materials (files and web links), assignments, announcements and gradebook in tabs.
- **Assignments and submissions** — deadlines with time zones, late policies with optional cutoffs, replaceable submissions, submission receipts, clear Not submitted / Overdue / Submitted / Submitted late / Graded states.
- **Grading** — points and written feedback, range validation, "Save & grade next" to work through the queue, concurrent-edit protection.
- **Gradebooks** — points-based current grade from graded work only; per-student view for students and a full course gradebook for instructors with **CSV export** for Excel or Google Sheets.
- **Light, dark and system themes** — switch instantly without losing what you typed; each user's choice is saved.
- **Reliable by design** — background tasks keep the window responsive, transactions keep data consistent, uploads never destroy a previous valid submission, and database outages show a "Try again" action.

## Screenshots

All screenshots come from the built-in demo data (`exec:java@seed-demo`).

### Sign in

![Sign-in screen](docs/screenshots/01-sign-in.png)

### Administrator — Swayam Shakya

| Dashboard | Users |
|---|---|
| ![Administrator dashboard](docs/screenshots/02-admin-dashboard.png) | ![User management](docs/screenshots/03-admin-users.png) |
| **Courses** | **Enrollments** |
| ![Course management](docs/screenshots/04-admin-courses.png) | ![Enrollment management](docs/screenshots/05-admin-enrollments.png) |

### Instructor — Dr. Savyata Ghimire

| Dashboard | Course materials |
|---|---|
| ![Instructor dashboard](docs/screenshots/06-instructor-dashboard.png) | ![Course materials](docs/screenshots/07-instructor-materials.png) |
| **Assignments** | **Grading (dark mode)** |
| ![Assignments](docs/screenshots/08-instructor-assignments.png) | ![Grading a submission in dark mode](docs/screenshots/09-grading-dark.png) |
| **Course gradebook with CSV export** | |
| ![Course gradebook](docs/screenshots/10-instructor-gradebook.png) | |

### Student — Krishna Bashyal and Surakshya Neupane

| Dashboard | Course assignments |
|---|---|
| ![Student dashboard](docs/screenshots/11-student-dashboard.png) | ![Student assignments](docs/screenshots/12-student-assignments.png) |
| **Submitting work** | **Submission receipt** |
| ![Choosing a file to submit](docs/screenshots/13-assignment-submit.png) | ![Submission receipt](docs/screenshots/14-submission-receipt.png) |
| **Grades** | **Grades (dark mode)** |
| ![Student grades](docs/screenshots/15-student-grades.png) | ![Student grades in dark mode](docs/screenshots/16-student-grades-dark.png) |
| **Profile and appearance (dark mode)** | |
| ![Profile and appearance settings](docs/screenshots/17-profile-appearance-dark.png) | |

## Quick start

1. Install **JDK 25** and **MySQL 8.4** (Maven is not needed — the project includes the Maven Wrapper).
2. Create the database: edit the two `CHANGE_ME` passwords in `database/01_create_database.sql`, then run `01_create_database.sql` and `02_schema.sql` (§2).
3. Copy `config/application.example.properties` to `config/application.properties` and set `db.password` (§3).
4. Load the demo data and start the app (§4–5):
   ```bash
   ./mvnw -q compile exec:java@seed-demo      # Windows: mvnw.cmd -q compile exec:java@seed-demo
   ./mvnw javafx:run                          # Windows: mvnw.cmd javafx:run
   ```
5. Sign in as `admin@inlearn.test` / `Admin#2026`.

Using **Visual Studio Code**? Follow the step-by-step guide in [§5.2](#52-visual-studio-code-step-by-step).

---

## Contents

1. [Prerequisites](#1-prerequisites)
2. [Database setup](#2-database-setup)
3. [Configuration](#3-configuration)
4. [First administrator or demo data](#4-first-administrator-or-demo-data)
5. [Running InLearn](#5-running-inlearn)
6. [Running the tests](#6-running-the-tests)
7. [Packaging](#7-packaging)
8. [Opening the FXML files in Scene Builder](#8-opening-the-fxml-files-in-scene-builder)
9. [Project structure and architecture](#9-project-structure-and-architecture)
10. [Roles and permissions](#10-roles-and-permissions)
11. [Business rules](#11-business-rules)
12. [Database design](#12-database-design)
13. [File storage](#13-file-storage)
14. [Appearance: light, dark and system themes](#14-appearance-light-dark-and-system-themes)
15. [Keyboard shortcuts](#15-keyboard-shortcuts)
16. [Deployment assumptions and known limitations](#16-deployment-assumptions-and-known-limitations)
17. [Troubleshooting](#17-troubleshooting)
18. [Publishing to GitHub](#18-publishing-to-github)
19. [Author and license](#19-author-and-license)

---

## 1. Prerequisites

| Component | Version | Notes |
|---|---|---|
| JDK | **25 (LTS)** or newer | JavaFX 25 requires JDK 23+; the build enforces 25+. Any vendor works (Temurin, Oracle, Zulu, Liberica). |
| Maven | not required | The included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9.11 on first use. An installed Maven 3.9+ works too. |
| MySQL Server | **8.4 LTS** (also works with 8.0 and 9.x) | `utf8mb4`, InnoDB, CHECK constraints (8.0.16+). |
| Visual Studio Code (optional) | current | With the **Extension Pack for Java**; see §5.2. |
| Scene Builder (optional) | 24 or newer | Only for editing layouts visually. |

Pinned library versions (see `pom.xml`): JavaFX **25.0.2**, MySQL Connector/J **9.5.0**, Bouncy Castle `bcprov-jdk18on` **1.81** (bcrypt), JUnit Jupiter **5.13.4**.

You do **not** need to install JavaFX or Maven separately — the wrapper downloads Maven, and Maven downloads the correct JavaFX build for your operating system. The first build needs an internet connection.

**About the commands in this README:** they are written as `./mvnw …` for macOS and Linux. On Windows use `mvnw.cmd …` instead (in PowerShell: `.\mvnw.cmd …`). If you have Maven installed, plain `mvn …` works everywhere.

## 2. Database setup

1. **Choose passwords.** Open `database/01_create_database.sql` and replace both `CHANGE_ME` placeholders with strong passwords (one for the application account, one for the test account).
2. **Create the databases and accounts** (as a MySQL administrator):
   ```bash
   mysql -u root -p < database/01_create_database.sql
   ```
   This creates `inlearn` (application) and `inlearn_test` (integration tests), plus the accounts `inlearn_app` (data access only — no schema changes) and `inlearn_test`.
3. **Create the tables:**
   ```bash
   mysql -u root -p inlearn < database/02_schema.sql
   ```

**With MySQL Workbench instead of a terminal:** connect as `root`, choose **File → Open SQL Script…**, open `01_create_database.sql`, replace the two `CHANGE_ME` values and click the lightning-bolt **Execute** button. In the **Schemas** panel click refresh, right-click **inlearn → Set as Default Schema**, then open and execute `02_schema.sql`. You should see 8 tables under `inlearn`.

To start over in development: `database/99_drop_tables_development_only.sql`, then `02_schema.sql` again.

## 3. Configuration

```bash
cp config/application.example.properties config/application.properties
```

Edit `config/application.properties` and set at least `db.password`. Every setting is explained in the example file:

| Setting | Purpose | Default |
|---|---|---|
| `db.url` | JDBC URL | `jdbc:mysql://localhost:3306/inlearn?sslMode=PREFERRED&allowPublicKeyRetrieval=true` |
| `db.username` / `db.password` | Database account | `inlearn_app` / — |
| `db.connectTimeoutSeconds` | Connection timeout | `8` |
| `storage.directory` | Where uploaded files are kept | `./inlearn-data/storage` |
| `storage.maxUploadMegabytes` | Upload size limit | `25` |
| `storage.allowedExtensions` | Permitted file types | common documents, images, code |
| `app.environment` | `development` or `production` (the demo seeder refuses production) | `development` |
| `app.timezone` | Zone for showing/entering deadlines | the computer's zone |
| `logging.directory` | Log files | `./inlearn-data/logs` |

`config/application.properties` is in `.gitignore`, so real passwords are never committed. You can also keep secrets out of files entirely with environment variables, which take priority: `INLEARN_DB_URL`, `INLEARN_DB_USERNAME`, `INLEARN_DB_PASSWORD`. A different config file can be chosen with `-Dinlearn.config=/path/file.properties` or `INLEARN_CONFIG`.

## 4. First administrator or demo data

InLearn has no public registration and **no built-in default credentials**. Choose one:

**A. A real installation — create the first administrator interactively:**
```bash
./mvnw -q compile exec:java@create-admin
```

**B. Development — load sample demo data** (only into an empty database; refuses to run when `app.environment=production`):
```bash
./mvnw -q compile exec:java@seed-demo
# wipe ALL existing InLearn data first and reseed:
./mvnw -q compile exec:java@seed-demo "-Dexec.args=--reset"
```

The seeder hashes passwords with the application's own bcrypt utility and creates 1 administrator, 2 instructors, 6 students, 3 courses, materials, announcements, upcoming and overdue assignments, and on-time, late and graded submissions. Deadlines are relative to when you run it.

| Role | Name | Email | Password (demo only) |
|---|---|---|---|
| Administrator | Swayam Shakya | `admin@inlearn.test` | `Admin#2026` |
| Instructor | Dr. Savyata Ghimire (CS 101, ENG 110) | `savyata.ghimire@inlearn.test` | `Teach#2026` |
| Instructor | Prof. Bidhan Thapa (MATH 201) | `bidhan.thapa@inlearn.test` | `Teach#2026` |
| Student | Krishna Bashyal | `krishna.bashyal@student.inlearn.test` | `Learn#2026` |
| Student | Felix Montalfu | `felix.montalfu@student.inlearn.test` | `Learn#2026` |
| Student | Vikas Saahil | `vikas.saahil@student.inlearn.test` | `Learn#2026` |
| Student | Surakshya Neupane | `surakshya.neupane@student.inlearn.test` | `Learn#2026` |
| Student | Aarav Karki | `aarav.karki@student.inlearn.test` | `Learn#2026` |
| Student | Prisha Adhikari | `prisha.adhikari@student.inlearn.test` | `Learn#2026` |

Good accounts to explore: **Krishna Bashyal** has graded work and grades in three courses; **Surakshya Neupane** has overdue work and an open project to submit; **Dr. Savyata Ghimire** has submissions waiting to be graded.

## 5. Running InLearn

### 5.1 Command line

```bash
./mvnw javafx:run          # Windows: mvnw.cmd javafx:run
```

Run it from the project folder so the default `config/application.properties` is found. The first run downloads Maven, JavaFX and the libraries (a few minutes); later runs start in seconds.

### 5.2 Visual Studio Code (step by step)

The project includes ready-made VS Code settings (`.vscode/`): one-click tasks for running the app, loading demo data and running tests, plus a Run/Debug configuration.

1. **Install the tools** (once):
   - **JDK 25** — for example Eclipse Temurin 25 from adoptium.net. On Windows, tick **"Set JAVA_HOME variable"** in the installer.
   - **MySQL Server 8.4** — the MySQL Installer on Windows can add **MySQL Workbench** too. Remember the root password you choose.
   - **Visual Studio Code** from code.visualstudio.com.
2. **Open the project:** in VS Code choose **File → Open Folder…** and select the `InLearn` folder (the one that contains `pom.xml`). If asked, click **Yes, I trust the authors**.
3. **Install the Java extensions:** VS Code shows *"Do you want to install the recommended extensions?"* → **Install** (this is the **Extension Pack for Java**). Wait until the status bar says the Java project is ready.
4. **Check Java:** open **Terminal → New Terminal** and run `java -version`. It must say 25. If it shows another version, set `JAVA_HOME` to the JDK 25 folder and restart VS Code.
5. **Create the database** (once) — follow §2 in a terminal or with MySQL Workbench: edit the two `CHANGE_ME` passwords in `database/01_create_database.sql`, run it, then run `database/02_schema.sql` against the `inlearn` database.
6. **Configure InLearn** (once): in the Explorer, right-click `config/application.example.properties` → **Copy**, then right-click the `config` folder → **Paste**, and rename the copy to `application.properties`. Open it and set `db.password` to the password you chose for `inlearn_app`. Save.
7. **Load the demo data** (once): **Terminal → Run Task… → InLearn: Load demo data**. The terminal ends by listing the demo accounts.
8. **Start InLearn:** **Terminal → Run Task… → InLearn: Run the app** (or press **Ctrl+Shift+B** / **Cmd+Shift+B**). The InLearn window opens.
9. **Explore:** sign in as `admin@inlearn.test` / `Admin#2026`, then sign out and try `savyata.ghimire@inlearn.test` / `Teach#2026` (instructor) and `krishna.bashyal@student.inlearn.test` / `Learn#2026` (student). Close the window to stop the app.

Other tasks under **Terminal → Run Task…**:

| Task | What it does |
|---|---|
| InLearn: Run the app | Starts InLearn (`mvnw javafx:run`) |
| InLearn: Load demo data | Fills an **empty** database with the demo accounts and courses |
| InLearn: Reset and reload demo data | **Deletes all InLearn data**, then reloads the demo |
| InLearn: Create first administrator | Prompts in the terminal for a real administrator account |
| InLearn: Run unit tests | Runs the tests that don't need a database |
| InLearn: Build runnable jar | Creates `target/inlearn-1.0.0-all.jar` |

**Debugging:** open the **Run and Debug** view and start **"InLearn (run or debug)"** to set breakpoints in the code. The tasks above remain the simplest way to just run the app.

## 6. Running the tests

```bash
./mvnw test       # unit tests (no database needed)
./mvnw verify     # unit tests + JDBC integration tests against inlearn_test
```

Integration tests (`*IT.java`) need the isolated test database. Configure it in one of these ways:

```bash
cp config/test-database.example.properties config/test-database.properties   # then edit the password
# or
./mvnw verify -Dinlearn.test.db.password=yourTestPassword
# or environment variables INLEARN_TEST_DB_URL / INLEARN_TEST_DB_USERNAME / INLEARN_TEST_DB_PASSWORD
```

The integration tests **drop and recreate every InLearn table** in that database, so they refuse to run unless the database name ends in `_test`. Without settings they are skipped (reported as skipped, not passed).

What is covered: bcrypt verification; sign-in lockout and its expiry; gradebook CSV export (content, escaping and permissions); role and ownership authorization (cross-course and cross-student access, admins cannot grade, deactivated/withdrawn users lose access immediately); duplicate enrollment prevention; deadline and late-policy behaviour using an injectable `MutableClock`; grade range validation; grade calculation with ungraded work; transaction rollback of a failed batch enrollment; a simulated database outage during a submission replacement (old file and record survive); path-traversal protection; time-zone conversion of deadlines.

A manual checklist for the JavaFX workflows is in [`docs/MANUAL_TEST_CHECKLIST.md`](docs/MANUAL_TEST_CHECKLIST.md).

## 7. Packaging

```bash
./mvnw package
java --enable-native-access=ALL-UNNAMED -jar target/inlearn-1.0.0-all.jar
```

`target/inlearn-1.0.0-all.jar` contains the application, JavaFX (for the operating system you built on) and all libraries; it starts through `com.inlearn.Launcher`. Build on each target OS (Windows, macOS, Linux) to get a jar with that platform's JavaFX natives. Run it from a folder containing `config/application.properties`, or pass `-Dinlearn.config=…`.

## 8. Opening the FXML files in Scene Builder

1. In Scene Builder choose **File → Open** and pick any file under `src/main/resources/com/inlearn/view/`.
2. To preview the real styling: **Preview → Scene Style Sheets → Add a Style Sheet…** and add `src/main/resources/com/inlearn/styles/components.css` **and** `theme-light.css` (or `theme-dark.css`). The component sheet relies on colours defined by a theme sheet, so add both.
3. Course workspaces (`instructor-course.fxml`, `student-course.fxml`) use `fx:include`; Scene Builder shows the included tabs automatically.

Controllers are created by `ViewLoader`, which passes each one a `UiContext`. The FXML keeps the normal `fx:controller` attribute, so Scene Builder can list `@FXML` fields and handlers.

## 9. Project structure and architecture

```
inlearn/
├── pom.xml
├── config/                       example configuration (real copies are git-ignored)
├── database/                     ordered SQL scripts (01 create, 02 schema, 99 dev reset)
├── docs/                         manual test checklist and screenshots
└── src/
    ├── main/java/com/inlearn/
    │   ├── InLearnApp, Launcher  JavaFX entry points
    │   ├── AppContext            wires DAOs and services together (composition root)
    │   ├── config/               AppConfig (settings), LoggingSetup
    │   ├── model/                records and enums: User, Course, Assignment, Submission, Grade, …
    │   ├── dao/                  JDBC with prepared statements; Database handles connections/transactions
    │   ├── service/              business rules, validation and authorization
    │   ├── exception/            user-facing exception hierarchy
    │   ├── util/                 PasswordHasher (bcrypt), FileStorage, InputValidator
    │   ├── tools/                CreateAdminTool, DemoDataSeeder (separate from normal startup)
    │   └── ui/
    │       ├── shell/            sidebar + top bar + navigation
    │       ├── auth/ admin/ instructor/ student/ common/   screen controllers
    │       ├── support/          TaskRunner, Notifier, Dialogs, FormDialog, UiFormats, …
    │       └── theme/            ThemeManager, ThemeToggle, LocalPreferences
    ├── main/resources/com/inlearn/
    │   ├── view/                 FXML layouts (28 files)
    │   └── styles/               components.css, theme-light.css, theme-dark.css
    └── test/java/com/inlearn/    unit tests (*Test) and JDBC integration tests (*IT)
```

**Layers.** Controllers only handle the screen: they read fields, call a service on a background thread (`TaskRunner`/`UiTask`) and display the result on the JavaFX thread. Services hold every rule and every permission check, and open connections or transactions through `Database`. DAOs contain all SQL. No SQL appears in controllers and no rules in FXML.

**Transactions.** `Database.inTransaction(...)` commits all-or-nothing; any exception rolls back. It is used for multi-step changes such as batch enrollment, submission replacement, due-date changes (which update late flags) and grading. Rows that must not change mid-decision are locked with `SELECT … FOR UPDATE`.

**Concurrency.** Editable tables carry a `version` column; updates use `WHERE id = ? AND version = ?`. If someone else saved first the user sees "This … was changed by someone else … Refresh". Buttons are disabled while their operation runs, preventing double submissions.

**Errors.** `InLearnException` subclasses carry plain-language messages. SQL errors are translated in one place (`Database.translate`): connection failures become "can't reach the database" with a **Try again** button; wrong credentials, missing database or missing tables get specific setup guidance. Unexpected errors are logged with stack traces; users see a short message. Passwords and hashes are never logged.

## 10. Roles and permissions

Enforced in the service layer (`AuthorizationService`), which reloads the account from the database for every protected action — hiding a button is never the only protection.

| Action | Administrator | Instructor | Student |
|---|:-:|:-:|:-:|
| Create, edit, search, deactivate users; assign roles | ✔ | — | — |
| Create, edit, archive courses; assign instructors | ✔ | — | — |
| Enroll and withdraw students | ✔ | — | — |
| View course content (materials, announcements) | — | own courses | enrolled courses |
| Edit course description; add/delete materials; post/edit announcements | — | own courses | — |
| Create/edit/delete assignments | — | own courses | — |
| View roster, download submissions, grade, view course gradebook | — | own courses | — |
| Submit work, see own submissions, grades and feedback | — | — | enrolled courses, own work only |
| Change own display name, password, appearance | ✔ | ✔ | ✔ |

Administrators deliberately **cannot grade** or download submissions. Deactivated accounts cannot sign in, and a signed-in user who is deactivated is signed out on their next action. Withdrawn students lose access to the course; their work stays on record.

## 11. Business rules

**Accounts.** Emails are unique (case-insensitive). Passwords: 8–64 characters with a letter and a number, hashed with bcrypt (cost 12). Sign-in errors are generic ("The email or password you entered is incorrect"), and unknown emails take the same time as wrong passwords. After 5 wrong passwords for an email, sign-in for that email pauses for 5 minutes (unknown emails are treated the same, so the message reveals nothing). The sign-in screen can remember the last email on that computer if the user ticks the box; passwords are never stored. The last active administrator can't be deactivated or demoted, and administrators can't deactivate or change the role of their own account. Role changes are refused while academic records point at the account (e.g. an instructor still assigned to courses — reassign them first; a student with enrollment history — create a separate account). Accounts can be deleted only if they have no records; otherwise deactivate.

**Courses.** Codes are unique and normalised (`cs 101` → `CS 101`). Archived courses are read-only for everyone; they can be restored. A course can be deleted only if it has no enrollments or content.

**Enrollments.** One row per student per course. Withdrawing marks it withdrawn; re-enrolling reactivates the same row. Batch enrollment is one transaction: if any selected student is already enrolled, nobody is enrolled and the message names the student.

**Submissions and deadlines.** Up to and including the due time, students can submit and replace freely unless the work is graded. After the deadline, *Late submissions not accepted* closes submission; *Accept late submissions* accepts them marked **late**, optionally until a cutoff. Graded work cannot be replaced. Statuses shown: **Not submitted**, **Overdue** (past due and nothing submitted), **Submitted · Awaiting feedback**, **Submitted late · Awaiting feedback**, **Graded**. Each successful upload shows a receipt with the file name, time and on-time/late status.

**Assignment edits never silently invalidate work.** Maximum points lock once any submission is graded. Changing the due date asks for confirmation and recalculates each submission's late flag in the same transaction. Changes that would reject already-accepted work (switching to "no late work" when late work exists, or a cutoff before an existing submission) are blocked with an explanation. Assignments with submissions can't be deleted.

**Gradebook export.** Instructors can save the course gradebook as CSV (UTF-8, opens in Excel/Numbers/Google Sheets). It uses the same words as the screen — "Not graded" and "Not submitted" — and neutralises cell values that spreadsheets would treat as formulas.

**Gradebook.** `current grade = earned points on graded assignments ÷ possible points on graded assignments × 100`. Ungraded work is excluded (shown as "Not graded", never zero); with nothing graded the grade is "N/A". Calculations use full-precision `BigDecimal`; only the displayed percentage is rounded (one decimal). Grades must be between 0 and the assignment's maximum.

## 12. Database design

All `DATETIME` columns store **UTC**; every connection sets `time_zone = '+00:00'`. The interface converts to the configured display zone and always prints the zone next to deadlines (e.g. "Tue, Oct 27, 2026 at 11:59 PM EDT"). Points use `DECIMAL(7,2)`. Foreign keys use `ON DELETE RESTRICT` — history is preserved by deactivating, archiving and withdrawing rather than deleting.

```mermaid
erDiagram
    USERS ||--o{ COURSES : "teaches (instructor_id)"
    USERS ||--o{ ENROLLMENTS : "is enrolled (student_id)"
    COURSES ||--o{ ENROLLMENTS : has
    COURSES ||--o{ COURSE_MATERIALS : has
    COURSES ||--o{ ASSIGNMENTS : has
    COURSES ||--o{ ANNOUNCEMENTS : has
    USERS ||--o{ COURSE_MATERIALS : uploads
    USERS ||--o{ ANNOUNCEMENTS : writes
    USERS ||--o{ ASSIGNMENTS : creates
    ASSIGNMENTS ||--o{ SUBMISSIONS : receives
    USERS ||--o{ SUBMISSIONS : submits
    SUBMISSIONS ||--o| GRADES : "is graded by"
    USERS ||--o{ GRADES : grades

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        enum role "ADMIN | INSTRUCTOR | STUDENT"
        enum status "ACTIVE | INACTIVE"
        enum theme_preference "SYSTEM | LIGHT | DARK"
        datetime last_login_at
        int version
    }
    COURSES {
        bigint id PK
        varchar code UK
        varchar title
        text description
        varchar term
        bigint instructor_id FK
        enum status "ACTIVE | ARCHIVED"
        int version
    }
    ENROLLMENTS {
        bigint id PK
        bigint course_id FK
        bigint student_id FK
        enum status "ACTIVE | WITHDRAWN"
        datetime enrolled_at
        datetime withdrawn_at
    }
    COURSE_MATERIALS {
        bigint id PK
        bigint course_id FK
        varchar title
        enum material_type "FILE | LINK"
        varchar link_url
        varchar original_filename
        varchar storage_key UK
        bigint uploaded_by FK
    }
    ASSIGNMENTS {
        bigint id PK
        bigint course_id FK
        varchar title
        text instructions
        datetime due_at
        decimal max_points
        enum late_policy "NO_LATE | ALLOW_LATE"
        datetime late_cutoff_at
        int version
    }
    SUBMISSIONS {
        bigint id PK
        bigint assignment_id FK
        bigint student_id FK
        varchar original_filename
        varchar storage_key UK
        datetime submitted_at
        boolean is_late
        int attempt_number
    }
    GRADES {
        bigint id PK
        bigint submission_id FK "unique"
        decimal points
        text feedback
        bigint graded_by FK
        datetime graded_at
        int version
    }
    ANNOUNCEMENTS {
        bigint id PK
        bigint course_id FK
        bigint author_id FK
        varchar title
        text body
    }
```

Unique constraints guarantee one enrollment per student per course `(course_id, student_id)`, one current submission per student per assignment `(assignment_id, student_id)`, one grade per submission `(submission_id)`, and unique course codes and emails. Every table has `created_at`/`updated_at`.

## 13. File storage

Uploaded files are **copied** into `storage.directory` (never just the path on the user's computer) under generated names such as `submissions/3f2a…e1.pdf`. MySQL stores that storage key, the original file name (for display) and the size.

- Size limit: `storage.maxUploadMegabytes` (default **25 MB**); empty files are rejected.
- Only extensions in `storage.allowedExtensions` are accepted.
- Path traversal is blocked: keys must match the generated format and resolve inside the storage root.
- Copies are written to a temporary file and then moved into place.
- Replacing a submission stores the new file first, switches the database row in a transaction, and deletes the old file only after the commit. If anything fails, the new copy is removed and the previous submission is untouched.
- Downloads are authorised by the service before the file is copied to a location the user picks.
- Missing or unreachable files produce a clear message (for example, a disconnected network drive).

**Multiple computers:** every InLearn installation that shares a database must point `storage.directory` at the **same** location (for example a mapped network share), otherwise files uploaded on one computer can't be opened on another.

## 14. Appearance: light, dark and system themes

- **Profile & settings → Appearance** offers **System**, **Light** and **Dark**. The moon/sun button in the top bar (or **Ctrl+Shift+L**) switches between light and dark.
- Changes apply instantly to every open window, dialog, popup and tooltip without reloading the screen, so the current page, typed text and selections are kept.
- Each user's choice is saved to their account (only the `theme_preference` column is written, so it never conflicts with other profile edits) and restored at sign-in. The sign-in screen has its own toggle whose choice is stored on the computer (Java Preferences).
- **System** uses JavaFX 25's `Platform.Preferences` colour-scheme API, which follows the OS setting live on Windows, macOS and Linux desktops that report it. If detection isn't available, System falls back to **Light**, and the Appearance section says so rather than pretending.

How it's built: `components.css` styles every control using named colours like `-il-surface` and `-il-text`; `theme-light.css` and `theme-dark.css` only define those colours (plus Modena's base colours). `ThemeManager` swaps the palette sheet on all windows. Status labels always state their meaning in text; colour is a secondary cue. Native file dialogs use the operating system's own styling.

## 15. Keyboard shortcuts

| Shortcut | Action |
|---|---|
| F5 | Refresh the current page |
| Ctrl+F | Focus the page's search field |
| Ctrl+N | New item (users, courses) |
| Ctrl+S | Save the current form (assignment editor, grading, course description, profile name) |
| Alt+N (grading screen) | Save & grade next |
| Alt+Left | Back |
| Ctrl+Shift+L | Toggle light/dark |
| Alt+underlined letter | Activate a button (e.g. Alt+S on "Save") |
| Enter / Esc | Default / cancel button in dialogs |

## 16. Deployment assumptions and known limitations

- **Trusted deployment.** The desktop client connects to MySQL directly over JDBC, and the permission checks run inside the client. This is appropriate for a local machine or a trusted campus/lab network. Anyone who holds the configured database credentials can bypass the application, so a desktop client **cannot by itself** protect data from an untrusted user. An untrusted, distributed deployment would need a server-side API that holds the database credentials and enforces authorization — the service layer is structured so it could move behind such a server.
- Use TLS (`sslMode=REQUIRED`/`VERIFY_IDENTITY`) and remove `allowPublicKeyRetrieval=true` when the database is not on the local machine.
- Shared storage must be reachable from every installation (see §13).
- No email notifications or password-reset emails; administrators reset passwords.
- The sign-in lockout counts attempts inside each running copy of InLearn. Someone with direct database access, or using several computers, is not limited by it — another reason a server is needed for untrusted deployments.
- The packaged jar contains JavaFX for one operating system; build it on each target OS.
- Grades are points-based only (no weighting, categories or letter grades).
- Materials can be added and deleted but not edited in place.

## 17. Troubleshooting

| Symptom | Fix |
|---|---|
| "InLearn could not find its configuration file" | Copy `config/application.example.properties` to `config/application.properties` and run from the project folder (or use `-Dinlearn.config`). |
| "InLearn can't reach the database right now" | Start MySQL; check host/port in `db.url`; check firewalls. Use **Try again** once fixed. |
| "The database rejected InLearn's username or password" | Fix `db.username`/`db.password`, or the password in `01_create_database.sql`. |
| "The database named in db.url doesn't exist yet" / "tables haven't been created" | Run `database/01_create_database.sql` and `database/02_schema.sql`. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` for local servers without TLS, or enable TLS on the server. |
| `Your password does not satisfy the current policy requirements` when running 01 | MySQL's password validation is on; choose a longer, mixed password. |
| "Error: JavaFX runtime components are missing" | Start with `./mvnw javafx:run`, the VS Code task, the "InLearn (run or debug)" launch configuration (main class `com.inlearn.Launcher`) or the `-all.jar` — not by running `InLearnApp` directly. |
| Maven enforcer: "requires JDK 25" | Install JDK 25 and point `JAVA_HOME` at it (`./mvnw -v` shows which JDK Maven uses). |
| Warnings about `System::load` / native access | Harmless; `./mvnw javafx:run` and the documented `java` command already pass `--enable-native-access`. |
| A file is "missing from InLearn's storage folder" | Check `storage.directory`; on shared deployments make sure the share is connected on this computer. |
| Integration tests are skipped | Configure `config/test-database.properties` (see §6). |
| No first account | Run `./mvnw -q compile exec:java@create-admin` (or the VS Code task **InLearn: Create first administrator**). |
| `./mvnw: Permission denied` (macOS/Linux) | Run `chmod +x mvnw` once. |
| `The JAVA_HOME environment variable is not defined correctly` | Point `JAVA_HOME` at the JDK 25 folder (not its `bin` folder), then open a new terminal or restart VS Code. |
| VS Code: tasks don't appear | Make sure you opened the folder that contains `pom.xml` (File → Open Folder), not a parent folder. |

Logs are written to `logging.directory` (`inlearn-0.log`, rotated).

## 18. Publishing to GitHub

This folder is already a Git repository on the `main` branch, with its history.

**With GitHub Desktop (no terminal):**

1. Install GitHub Desktop from desktop.github.com and sign in as **RippedPaper**.
2. **File → Add local repository…** → choose this `InLearn` folder (the one containing `README.md`) → **Add repository**.
3. Click **Publish repository**, keep the name `InLearn`, untick **Keep this code private** if it should be public, and click **Publish repository**.

**With the terminal:** create an **empty** repository named `InLearn` on GitHub (no README, license or .gitignore), then:

```bash
git remote add origin https://github.com/RippedPaper/InLearn.git
git push -u origin main
```

After publishing, the **CI** workflow (`.github/workflows/ci.yml`) runs automatically on GitHub: it starts MySQL 8.4, builds with JDK 25, runs all unit and integration tests and uploads the runnable Linux jar as a build artifact (see the **Actions** tab).

## 19. Author and license

Created by **Swayam Shakya** ([@RippedPaper](https://github.com/RippedPaper)).

Released under the [MIT License](LICENSE) — © 2026 Swayam Shakya (RippedPaper). Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).
