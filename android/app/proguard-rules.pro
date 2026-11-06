# kotlinx.serialization keeps generated serializers; Retrofit uses them reflectively.
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.example.tempnumber.data.** { *; }
-keepattributes *Annotation*, Signature
