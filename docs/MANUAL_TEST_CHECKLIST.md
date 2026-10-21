# Manual test checklist (JavaFX workflows)

Run against a database seeded with `./mvnw -q compile exec:java@seed-demo "-Dexec.args=--reset"` (or the VS Code task **InLearn: Reset and reload demo data**). Tick each item in **both light and dark mode** where marked ◐.

## Acceptance workflow

- [ ] 1. Sign in as `admin@inlearn.test`. Dashboard shows active students, instructors, courses, enrollments. ◐
- [ ] 2. Users → **New user** (Ctrl+N): create an instructor, then a student. A success toast appears; both show in the table.
- [ ] 3. Courses → **New course**: code `ds 210` (saved as `DS 210`), assign the new instructor. Select the row → **Manage enrollments** → select the student → **Enroll selected**.
- [ ] 4. Sign out; sign in as the instructor. The course appears in the sidebar and on the dashboard.
  - [ ] Materials → **Add web link…** and **Add file…** (native file chooser). Both appear in the table.
  - [ ] Announcements → **Post announcement**.
  - [ ] Assignments → **New assignment**: deadline shows the time zone; save with Ctrl+S; you return to the Assignments tab.
- [ ] 5. Sign in as the student: dashboard shows the deadline and announcement. Open the assignment, **Choose file…**, **Submit assignment**. A receipt shows file name, time and "On time".
- [ ] 6. Sign in as the instructor: Assignments → **Review submissions** → select the student → **Download submission** (saves a copy) → enter `25` (rejected: max is the assignment's points) → enter a valid grade and feedback → **Save grade**. ◐
- [ ] 7. Sign in as the student: Grades shows the points, feedback and current percentage; "Graded" status. ◐
- [ ] 8. Close and restart InLearn: all data is still there.
- [ ] 9. As a student, confirm only enrolled courses appear; as an instructor, only assigned courses. (Cross-course access is also covered by `AuthorizationIT`.)
- [ ] 10. Errors: duplicate email and duplicate course code show clear messages; stop MySQL and sign in → "can't reach the database"; Try again works after restarting MySQL.

## Rules and states

- [ ] Overdue: as `surakshya.neupane`, CS 101 Lab 1 shows **Overdue**; submit button is closed with an explanation (no late work).
- [ ] Late: as `surakshya.neupane`, CS 101 Lab 2 accepts a submission marked late.
- [ ] Replacement before the deadline asks for confirmation and increments the attempt number.
- [ ] Graded work can't be replaced (as `krishna.bashyal`, CS 101 Lab 1).
- [ ] Assignment editor: with graded submissions, maximum points are locked with an explanation; changing the due date asks to recalculate late status.
- [ ] Archive a course: everything becomes read-only with a banner; restore it.
- [ ] Deactivate a user: they can't sign in ("Your account is inactive"); reactivate.
- [ ] Changing an instructor's role while they teach courses is refused with guidance.
- [ ] Unsaved changes: type in a form (course description, assignment editor, grade) and navigate away → confirmation.

## Theme and accessibility

- [ ] Header toggle and Ctrl+Shift+L switch theme instantly; text typed in a form is kept. ◐
- [ ] Profile → Appearance: System/Light/Dark; the note states whether the OS setting was detected.
- [ ] Sign out → the sign-in screen uses its own (computer-level) appearance; sign back in → the user's choice returns. Two different users keep different choices.
- [ ] Dialogs, combo-box lists, date pickers, tooltips and toasts are readable in both themes. ◐
- [ ] Tab moves through every control with a visible focus ring; Enter activates default buttons; Esc cancels dialogs.
- [ ] Status labels read correctly with colour ignored (text says Overdue, Graded, etc.).
- [ ] Resize the window to its minimum (960×640): no clipped controls; tables scroll.

## Added features

- [ ] Sign-in: enter a wrong password 5 times → "Too many unsuccessful sign-in attempts…"; even the right password is refused for 5 minutes, then works.
- [ ] Tick "Remember my email on this computer", sign in and out → the email is prefilled and the cursor is in Password. Untick it and sign in → nothing is remembered next time.
- [ ] Instructor gradebook → **Export CSV…** → open the file in a spreadsheet: one row per student, "Not graded"/"Not submitted" words, current grade column.
- [ ] Grading screen → **Save & grade next** (Alt+N) saves and opens the next student whose work still needs grading; on the last one the toast says every submission is graded.
