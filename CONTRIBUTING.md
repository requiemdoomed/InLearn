# Contributing to InLearn

Thanks for your interest in improving InLearn!

## Getting set up

Follow the README sections 1–5 to install JDK 25 and MySQL (Maven comes with the project as `mvnw`), create the databases and run the app. Load demo data with `./mvnw -q compile exec:java@seed-demo` (Windows: `mvnw.cmd …`).

## Before opening a pull request

1. Run `./mvnw verify` with a configured `inlearn_test` database (README §6). All tests must pass.
2. Keep the layers separate: SQL only in `dao`, rules and permission checks only in `service`, screen logic only in `ui`.
3. Every protected service method must call `AuthorizationService` — hidden buttons are not security.
4. User-facing errors are `InLearnException` subclasses with plain-language messages; never show SQL or stack traces.
5. Run database and file work through `TaskRunner`, never on the JavaFX thread.
6. Styles use only the `-il-*` colour names; check new screens in both light and dark mode.
7. Add or update tests for behaviour changes, and update the README if setup or rules change.

## Code style

Four-space indentation, descriptive names, focused methods, and comments that explain *why* rather than *what*. See `.editorconfig`.

## Reporting security problems

Please don't open a public issue for security vulnerabilities. Contact the maintainer, Swayam Shakya ([@RippedPaper](https://github.com/RippedPaper)), privately first.
