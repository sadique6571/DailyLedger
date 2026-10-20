# Preserve JNI and reflection contracts; libraries provide additional consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class com.sadique.dailyledger.data.AppDatabase { *; }
-keep class com.sadique.dailyledger.data.AppDatabase_Impl { *; }
# The CI release attestation check verifies this provider by its original descriptor.
-keep class com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory { *; }
