# Daily Ledger UI / Experience Upgrade

Implemented in this project copy:

- Premium dashboard balance card with animated progress and spent/left percentages.
- Spending-by-category visual and recent transactions.
- Manual-city current weather (no GPS permission) using Open-Meteo and cached refresh.
- Retro-soft transaction sounds with Settings toggle.
- Modernized AI Auto Fill review cards.
- Google Drive backup status controls including Back up now and Restore.
- Fresh-install **Backup found -> Restore / Skip** flow; no automatic destructive restore.
- Refined summary cards, section cards and existing theme support.

Server/security work is also included in this copy: Firebase AI Logic migration, App Check enforcement and CI test updates. Drive backup uses readable account-bound JSON; Firestore rules are read-only for legacy backups. See `ARCHITECTURE.md`, `GOOGLE-SETUP.md` and `TESTING.md`.
