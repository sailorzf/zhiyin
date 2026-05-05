# Voice engine
-keep class com.myyinshu.voice.** { *; }

# iFlytek (Xunfei) SDK
-keep class com.iflytek.** { *; }
-keep class com.iflytek.cloud.** { *; }
-keep interface com.iflytek.** { *; }
-keep class com.iflytek.msc.** { *; }
-dontwarn com.iflytek.**

# Vosk SDK
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.ibm.icu.** { *; }

# DataStore / Proto
-keep class androidx.datastore.** { *; }

# Kotlin serialization
-keepnames class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**
