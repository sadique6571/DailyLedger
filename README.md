# Daily Ledger

An Android personal finance app for income, expenses, savings, loans, udhar (credit) and kameti (committee savings) — built for everyday use in Pakistan, with Urdu and English interfaces. Records live on the phone and stay fully usable offline.

**Current version: 2.0.0 · build 51** · Package: `com.sadique.dailyledger` · Minimum Android: 8.0 (API 26)

**Status: feature-complete (final).** The core ledger, AI features, encrypted cloud backup and the full animation pass are all merged to `main`.

## Core features

- **Ledger:** income/expense history, salary scheduling, category search with Urdu/Roman Urdu aliases, custom categories, amount-in-words (Lakh/Crore), duplicate-entry warnings.
- **Savings, Loans, Udhar, Kameti:** separate trackers for savings goals, borrowed/lent loans, shop udhar with per-person statements, and personal/organizer kameti with installments and shares.
- **Insights:** spending donut, monthly comparisons, budgets with 80%/100% alerts, bill reminders, and shareable monthly summaries (PDF/CSV).
- **AI:** an offline **Mini AI** on the device (money score, saving rate, spending suggestions — no internet needed) plus **online AI (opt-in):** text-to-entry Auto Fill, an AI assistant (monthly report, budget advice, savings plan, debt order), AI udhar reminders, AI statement summaries, statement import, and automatic category learning. Online AI runs through a privacy-conscious Cloudflare proxy by default — **no API key is needed in the app** — and only small summaries (never the full ledger or private entries) are sent. Power users can instead use their own API key for Gemini, OpenAI, Groq, OpenRouter, DeepSeek, Mistral or any OpenAI-compatible endpoint (Settings → Mini AI & Auto Fill).
- **Interface:** 10 themes including AMOLED and Material You, accent colour picker, Nastaleeq Urdu with RTL, privacy mode for amounts, PIN/biometric app lock, home-screen widget, global search, a customizable dashboard, and an animated weather scene (real sun/moon phases, rain, snow and storm scenes). A full motion pass runs throughout: cards slide in from the left and replay as they scroll back into view, inner boxes cascade in sequence, amounts count up, and small celebrations mark saves and payments.

## Backup — Cloudflare, encrypted

- Automatic cloud backup: after edits, every 3 hours in the background, and a catch-up check when the app opens.
- **Protected with 256-bit AES encryption:** every backup is encrypted on the backup server (Cloudflare) before storage; the latest **three copies** are kept, and any of them can be restored to a new phone using the same Daily Ledger account.
- Local records are additionally encrypted on the phone itself (SQLCipher).
- Sign-in uses a Google account (Firebase Authentication). The app requests only four permissions: Internet, network state, notifications and biometric.

## Building

Builds run on **GitHub Actions** (`.github/workflows/build-apk.yml`): every push to a pull request compiles the app, runs unit tests and 155 emulator UI tests, verifies the release APK signature, and uploads the installable APK as the `DailyLedger-APK` artifact. Each build first purges stale Actions caches. The signing certificate is stable across builds, so new APKs install as in-place updates.

## Privacy

Daily Ledger is distributed free, directly to users (not via Google Play). Financial records stay on the device and in the owner's own encrypted cloud backup. The AI features are optional, can be turned off in Settings, and never send private entries or notes.
