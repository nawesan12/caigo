# kotlinx.serialization: conservar los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ar.com.caigo.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ar.com.caigo.app.**$$serializer { *; }
# MapLibre usa JNI.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
