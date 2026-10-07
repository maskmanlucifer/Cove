package app.cove.companion.ai.provider.ondevice

import app.cove.companion.ai.model.DetailKind
import app.cove.companion.ai.model.FoundDetail
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractionRemoteModel
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Finds dates, amounts, phone numbers and the like in a sentence. Implementations run on the phone and never use the network to read text. */
interface EntityReader {
    /** True when the reader can answer now. When its model is missing it starts fetching it (on Wi-Fi only) and says false. */
    suspend fun ready(): Boolean

    /** The details in [text]; [nowMs] resolves words like "tomorrow". */
    suspend fun read(text: String, nowMs: Long): List<FoundDetail>
}

/** [EntityReader] over ML Kit Entity Extraction (English model, downloaded once). */
class MlKitEntityReader : EntityReader {
    private val model = EntityExtractionRemoteModel.Builder(EntityExtractorOptions.ENGLISH).build()
    private val extractor by lazy { EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()) }
    private var downloadStarted = false

    override suspend fun ready(): Boolean {
        val manager = RemoteModelManager.getInstance()
        if (manager.isModelDownloaded(model).await()) return true
        if (!downloadStarted) {
            downloadStarted = true
            manager.download(model, DownloadConditions.Builder().requireWifi().build())
        }
        return false
    }

    override suspend fun read(text: String, nowMs: Long): List<FoundDetail> {
        val params = EntityExtractionParams.Builder(text).setReferenceTime(nowMs).setReferenceTimeZone(TimeZone.getDefault()).build()
        return extractor.annotate(params).await().flatMap { annotation ->
            annotation.entities.mapNotNull { e -> detail(e, annotation.start, annotation.end, annotation.annotatedText) }
        }
    }

    private fun detail(e: Entity, start: Int, end: Int, text: String): FoundDetail? = when (e.type) {
        Entity.TYPE_DATE_TIME -> e.asDateTimeEntity()?.let {
            FoundDetail(DetailKind.DateTime, start, end, text, epochMillis = it.timestampMillis, hasTime = it.dateTimeGranularity >= DateTimeEntity.GRANULARITY_HOUR)
        }
        Entity.TYPE_MONEY -> e.asMoneyEntity()?.let {
            FoundDetail(DetailKind.Money, start, end, text, amount = it.integerPart + it.fractionalPart / 100.0, currency = it.unnormalizedCurrency)
        }
        Entity.TYPE_PHONE -> FoundDetail(DetailKind.Phone, start, end, text)
        Entity.TYPE_FLIGHT_NUMBER -> e.asFlightNumberEntity()?.let { FoundDetail(DetailKind.Flight, start, end, text, label = "flight ${it.airlineCode} ${it.flightNumber}") }
        Entity.TYPE_TRACKING_NUMBER -> e.asTrackingNumberEntity()?.let { FoundDetail(DetailKind.Tracking, start, end, text, label = "parcel ${it.parcelTrackingNumber}") }
        Entity.TYPE_ADDRESS -> FoundDetail(DetailKind.Address, start, end, text)
        Entity.TYPE_EMAIL -> FoundDetail(DetailKind.Email, start, end, text)
        Entity.TYPE_URL -> FoundDetail(DetailKind.Url, start, end, text)
        else -> null // payment cards and IBANs are never read
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
