# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kascorp.webhooknotesender.**$$serializer { *; }
-keepclassmembers class com.kascorp.webhooknotesender.** {
    *** Companion;
}
-keepclasseswithmembers class com.kascorp.webhooknotesender.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.kascorp.webhooknotesender.data.local.entity.** { *; }

# Readable stack traces in crash reports
-keepattributes SourceFile, LineNumberTable
