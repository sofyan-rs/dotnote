# R8 rules for release builds.

# JGit references Java desktop/server APIs (JMX, Kerberos, SLF4J bindings) in code paths
# Android never runs. They don't exist on Android, so tell R8 not to warn about them.
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.**

# JGit loads its message texts (JGitText and friends) and some classes by reflection,
# so keep it whole. Costs some APK size; safer than chasing reflection one class at a time.
-keep class org.eclipse.jgit.** { *; }

# age encryption (kage) and its crypto: keep to be safe with reflective provider lookups.
-keep class kage.** { *; }
