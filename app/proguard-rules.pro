# Room entities are read by generated code and by sync (kotlinx.serialization on the same classes).
-keep class app.cove.companion.data.local.entity.** { *; }

# kotlinx.serialization: keep generated serializers and companions of @Serializable classes.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Ktor (OkHttp engine) references optional JVM-only classes.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class kotlin.coroutines.Continuation

# SQLCipher: JNI looks up these classes and members by name.
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
-dontwarn net.zetetic.**

# ML Kit GenAI and Play services (auth, credentials, tasks) ship consumer rules; silence optional references.
-dontwarn com.google.mlkit.genai.**
-dontwarn com.google.android.gms.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.playservices.** { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance widgets and manifest components are referenced by name.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
