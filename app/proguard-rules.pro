# Minification is disabled by default. These rules are here so the release
# build keeps working if you turn `isMinifyEnabled = true` on later.

# Room generates implementations via annotations; keep nothing special needed,
# but keep entities/DAOs to be safe if you obfuscate.
-keep class com.tome.reader.data.local.** { *; }

# junrar uses some reflection internally for its model classes.
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Coil
-dontwarn coil.**

# Keep Kotlin metadata for reflection-friendly libraries.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
