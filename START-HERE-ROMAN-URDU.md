# Daily Ledger 1.7.16 — shuru karne ka tareeqa

Yeh app income, kharcha, savings, loans aur kameti ka hisaab rakhti hai. Aapke records phone par local database mein rehte hain; local database SQLCipher se encrypted hai.

## Google Drive backup

1. Settings mein **Google Drive backup** kholein aur **Back up now** dabayein.
2. Google account ke liye Drive permission approve karein. Iske liye `drive.appdata` permission use hoti hai.
3. Backup complete hone ka status dekhein. Sirf switch ON hona kaafi nahi—successful backup status zaroor check karein.
4. Naye phone par isi Daily Ledger account aur backup wale Google account se sign in karke **Restore** karein.

Backup aapke Google Drive ke private hidden app-data folder mein readable JSON ke taur par save hota hai. Alag passphrase ya backup encryption nahi hai; Google account/Drive permission jiske paas hogi woh backup parh sakta hai. Apne Google account ko secure rakhein. Phone ka local SQLCipher encryption alag hai aur barqarar hai.

Purane Firestore backups aur pehle ke encrypted Drive backup files is version mein khud-ba-khud migrate/restore nahi hote. Purana phone aur uska data delete/uninstall na karein jab tak naya Drive backup bana kar restore test na kar lein. Zaroorat par purane app version se purana backup restore karke naya Drive backup banayein.

Automatic sync agar dusre phone ki nayi Drive version dekhe to overwrite karne ke bajaye ruk jata hai. Pehle latest backup Restore karein, ya Settings se **Back up now** ki confirmation de kar is phone ki ledger se replace karein.

## AI

Chat nahi hai: sirf suggestions aur review wali Auto Fill. Common entries phone par offline parse hoti hain: `aj doodh 150 papad 100 sabzi 100` ya `salary 60,000 aur petrol 2k`. Iske liye internet ya paid AI nahi chahiye. Optional Cloud AI alag consent se chalta hai; enable karne se pehle uski data-sharing details parhein.

Update APK ko purani app ke upar install karein; app data clear ya uninstall na karein.

Google setup ke liye `GOOGLE-SETUP.md`, aur optional AI ke liye `FIREBASE-AI-SETUP.md` dekhein.
