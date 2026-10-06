package app.cove.companion.feature.connect

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Package name and the fingerprints Google Cloud asks for when you create the Android OAuth client. */
data class SigningInfo(val packageName: String, val sha1: String, val sha256: String)

/** `AA:BB:CC` form of [bytes], as shown in Google Cloud and by `keytool`. */
fun fingerprint(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it) }

/** SHA-1 and SHA-256 of the certificate this installed build is signed with, or null when unreadable. */
fun readSigningInfo(context: Context): SigningInfo? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return null
    SigningInfo(
        context.packageName,
        fingerprint(MessageDigest.getInstance("SHA-1").digest(cert)),
        fingerprint(MessageDigest.getInstance("SHA-256").digest(cert)),
    )
}.getOrNull()
