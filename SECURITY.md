# Security reports

Report security issues through [GitHub private vulnerability reporting](https://github.com/agneswd/Stillpoint/security/advisories/new).
Do not include notification text, backup files, or other private phone data in public issues.

Stillpoint has no network permission. Backups are local JSON files without encryption.
Accessibility blocking helps users follow their own limits. It is not a device-management security boundary.

Only debug builds contain the gallery and shell-only storage checks.
Release builds must omit those components and the `INTERNET` permission.
