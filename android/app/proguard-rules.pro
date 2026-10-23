# SkillTrack reads and writes Firestore documents as plain maps, so no model
# classes need keeping. WorkManager, Compose and Navigation ship consumer rules.
#
# Keep line numbers so crash reports from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager finds its Play Services implementation by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
