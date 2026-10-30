# OmniChat R8 rules
# OkHttp / Okio (platform-specific optional deps)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
# App data classes are serialized manually via org.json (no reflection), but keep names readable in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Kotlin coroutines
-dontwarn kotlinx.coroutines.debug.**
