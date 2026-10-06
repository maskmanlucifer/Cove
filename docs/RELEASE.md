# Releasing Cove

Cove is sideloaded (see `PLAN.md` section 2). A release build is minified with R8, has resources shrunk and is signed with your own key.

## 1. Create a signing key (once)
```
keytool -genkeypair -v -keystore cove-release.jks -alias cove -keyalg RSA -keysize 4096 -validity 10000
```
Keep `cove-release.jks` somewhere safe and backed up; losing it means you cannot update an installed release in place.
Then create `keystore.properties` in the repo root (it is git-ignored, as are `*.jks`/`*.keystore`):
```
storeFile=cove-release.jks
storePassword=...
keyAlias=cove
keyPassword=...
```
`storeFile` is relative to the repo root. Without `keystore.properties` the release build is signed with the debug keystore, so local builds still install; such an APK is not suitable to distribute.
Add the release key's SHA-1 (`./gradlew signingReport`) to the Google OAuth Android client (see `docs/SETUP.md`).

## 2. Build
```
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew :app:bundleRelease       # app/build/outputs/bundle/release/app-release.aab (Play / Firebase App Distribution)
```
Backend keys (`supabaseUrl`, `supabaseAnonKey`, `googleWebClientId`) come from `~/.gradle/gradle.properties` and are baked into the build; blank values switch cloud features off.

## 3. Sideload
```
adb install -r app/build/outputs/apk/release/app-release.apk
```
Updating over a build signed with a different key needs an uninstall first (this wipes local data unless it is backed up from Me > Back up now). Debug extras (`--ez seed true` and friends) only exist in debug builds.

## 4. R8 / keep rules
`app/proguard-rules.pro` keeps what reflection or JNI needs:
- Room entities (`data.local.entity.**`, also read by kotlinx.serialization for sync).
- kotlinx.serialization generated serializers and companions of `@Serializable` classes.
- SQLCipher (`net.zetetic.**`, `net.sqlcipher.**`): native code looks classes up by name.
- WorkManager workers (instantiated by class name), Glance receivers and callbacks, googleid / Credential Manager Play services classes.
- `-dontwarn` for optional Ktor/JVM-only references. ML Kit GenAI, Play services and Compose ship consumer rules.
After changing dependencies, build the release APK and run through launch, to-do, alarm, expense, journal entry, a typed voice command and the widgets, then check `adb logcat -b crash`.

## 5. Lint
`./gradlew :app:lintDebug` reports no errors. Remaining warnings are dependency-update hints and the following deliberate exceptions:
- `UnsafeProtectedBroadcastReceiver` on the two boot receivers: they only resync alarms/nudges, so a spoofed broadcast is harmless (`tools:ignore` in the manifest).
- `ExifInterface`: `android.media.ExifInterface` is used to avoid a new dependency; it is adequate for the rotation tag we read.
- `CredentialManagerMisuse`: all `GetCredentialException`s (which include `NoCredentialException`) are handled together.
- `HardcodedText`/`ContentDescription` in `widget_preview_*` layouts: they are static launcher previews.
No `abortOnError` override is needed.
