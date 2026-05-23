# JGit Core Reflection Handling Protections
-keep class org.eclipse.jgit.** { *; }
-keep interface org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# Preserve serialization naming strategies within underlying compression layers
-keepclassmembers class org.eclipse.jgit.lib.CoreConfig { *; }
-keepenum org.eclipse.jgit.** { *; }

# Slf4j Logging Bridge Diagnostics Mappings Protections
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Prevent obfuscation of JGit internal message bundle localization keys
-keepclassmembers class * extends org.eclipse.jgit.nls.TranslationBundle {
    public static *** get();
}