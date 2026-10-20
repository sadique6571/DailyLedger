# Build 33 — smaller app, editable categories and everyday food

Version remains 2.0.0 and the existing certificate is retained. R8 code/resource shrinking reduces the release APK without removing supported processor architectures, Urdu font, SQLCipher, offline receipt OCR, online AI or Money tools.

Receipt attachment controls/picker/viewing UI are removed from payments. Existing stored proofs and schema/backup v11 remain compatible to avoid deleting user data. No new proof attachments can be added in the UI.

Category search and committed selection are separate. Clearing or typing invalidates the prior category; it stays blank on blur until selected again. Blank intermediate searches are not learned. Auto-fill saving requires a nonblank category. Search, aliases and full lazy scrolling remain.

Added Salan, Packaged milk (Milk Pak/Milk Pack/Olpers), Chocolate, Toffee & candy, Papar, Biscuits, Chips & nimko, Chana breakfast, Paratha and Halwa puri, with Urdu labels and icons. Offline inference and the online prompt/normalizer distinguish them from generic Milk, Snacks, Food and Other. Plain doodh remains Milk. Explicit user learning continues to apply afterwards.

Local encryption, Firebase authentication/App Check, Drive login-only backup/restore and financial calculations are retained. Drive backups remain readable JSON, not E2EE. R8 uses library consumer rules plus explicit native SQLCipher/Room/attestation protections. CI runs the debug regression suite and a startup smoke check of the actual minified release on its disposable emulator.

Native libraries are compressed inside the APK and extracted by Android for the installed device ABI. A resource keep file preserves dynamically looked-up Firebase/OAuth configuration, and CI requires the minified release Google sign-in button to remain enabled.
