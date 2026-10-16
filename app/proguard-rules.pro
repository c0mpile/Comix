# Add project specific ProGuard rules here.
# Keep Kotlin Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-if class * { @kotlinx.serialization.Serializable <fields>; }
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
