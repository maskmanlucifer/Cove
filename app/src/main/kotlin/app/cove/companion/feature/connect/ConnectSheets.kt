package app.cove.companion.feature.connect

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.data.config.CredentialField
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.me.SheetCaption
import app.cove.companion.feature.me.SheetHeading
import app.cove.companion.feature.plan.PlanSheet

/** The sheets of the Connect screen. */
enum class ConnectSheet { Code, Supabase, Google, Drive, Gemini, Share }

/** Hosts the sheet named by [sheet]. */
@Composable
fun ConnectSheets(sheet: ConnectSheet?, ui: ConnectUi, vm: ConnectViewModel, onDismiss: () -> Unit) {
    when (sheet) {
        null -> Unit
        ConnectSheet.Code -> CodeSheet(vm, onDismiss)
        ConnectSheet.Share -> ShareCodeSheet(vm, onDismiss)
        ConnectSheet.Supabase -> SupabaseSheet(ui, vm, onDismiss)
        ConnectSheet.Google -> GoogleSheet(ui, vm, onDismiss)
        ConnectSheet.Drive -> DriveSheet(ui, vm, onDismiss)
        ConnectSheet.Gemini -> GeminiSheet(ui, vm, onDismiss)
    }
}

@Composable
internal fun ScrollingSheetContent(title: String, purpose: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading(title)
        SheetCaption(purpose)
        content()
    }
}

/** Editable drafts of [fields], seeded from what is saved. */
@Composable
private fun rememberDrafts(ui: ConnectUi, fields: List<CredentialField>) =
    remember { mutableStateOf(fields.associateWith { ui.credentials[it] }) }

@Composable
private fun FieldInputs(
    fields: List<CredentialField>,
    drafts: Map<CredentialField, String>,
    errors: Map<CredentialField, String>,
    context: Context,
    onChange: (CredentialField, String) -> Unit,
) {
    fields.forEach { f ->
        val hint = fieldHint(f)
        CredentialInput(
            hint.label, drafts[f].orEmpty(), { onChange(f, it) }, { readClipboard(context)?.let { v -> onChange(f, v.trim()) } },
            placeholder = hint.placeholder, secret = hint.secret, error = errors[f],
        )
    }
}

@Composable
private fun SupabaseSheet(ui: ConnectUi, vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fields = ServiceId.Supabase.fields
    var drafts by rememberDrafts(ui, fields)
    var errors by remember { mutableStateOf(emptyMap<CredentialField, String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    val url = drafts[CredentialField.SupabaseUrl].orEmpty()
    ScrollingSheetContent(
        "Supabase", "Your own private space online. Cove copies your data there so it syncs between devices. The free plan is plenty.",
        onDismiss,
    ) {
        Steps(
            listOf(
                "Create a free project at supabase.com.",
                "Open the SQL editor and run Cove's setup SQL (copy it below).",
                "In Project Settings, API, copy the Project URL and the anon public key.",
                "Paste both here and tap Test connection.",
            ),
        )
        SheetAction("Open dashboard", { openLink(context, ConnectLinks.supabaseApi(url)) })
        SheetAction("Copy setup SQL", { note = copySql(context, vm) })
        FieldInputs(fields, drafts, errors, context) { f, v -> drafts = drafts + (f to v) }
        SaveAndTest(ui, vm, ServiceId.Supabase, drafts, "Test connection", { errors = it }, { note = it }, context)
        val result = ui.tests[ServiceId.Supabase]
        if (result?.offerSetupSql == true) SheetAction("Copy setup SQL", { note = copySql(context, vm) })
        if (ui.signedIn) SheetAction("Sync now", { vm.syncNow(); note = "Sync queued." })
        note?.let { CoveText(it, style = CoveType.Meta, color = Cove.colors.muted) }
    }
}

private fun copySql(context: Context, vm: ConnectViewModel): String {
    val sql = vm.setupSql(context) ?: return "The setup SQL is missing from this build."
    copyToClipboard(context, "Cove setup SQL", sql)
    return "Setup SQL copied. Paste it into the Supabase SQL editor and run it."
}

@Composable
private fun GoogleSheet(ui: ConnectUi, vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fields = ServiceId.Google.fields
    var drafts by rememberDrafts(ui, fields)
    var errors by remember { mutableStateOf(emptyMap<CredentialField, String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    ScrollingSheetContent(
        "Google sign-in", "Signs you in with your Google account so your space stays yours. It goes through your Supabase project.",
        onDismiss,
    ) {
        Steps(
            listOf(
                "In Google Cloud, set up the OAuth consent screen and publish it (In production).",
                "Create an OAuth client of type Web application and copy its Client ID.",
                "In Supabase, Authentication, Providers, Google: turn it on and paste the Client ID and secret.",
                "Paste the Client ID here, then tap Sign in.",
            ),
        )
        SheetAction("Open Google Cloud credentials", { openLink(context, ConnectLinks.GOOGLE_CREDENTIALS) })
        if (ui.credentials.hasSupabase) SheetAction("Open Supabase providers", { openLink(context, ConnectLinks.supabaseProviders(ui.credentials.supabaseUrl)) })
        FieldInputs(fields, drafts, errors, context) { f, v -> drafts = drafts + (f to v) }
        if (ui.signedIn) {
            CoveText("Signed in${ui.email?.let { " as $it" }.orEmpty()}.", style = CoveType.Meta, color = Cove.colors.saved)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillButton("Save", { errors = vm.save(drafts); note = if (errors.isEmpty()) "Saved." else null }, Modifier.weight(1f), height = 48.dp)
                PillButton("Sign out", { vm.signOut() }, Modifier.weight(1f), kind = ButtonKind.Secondary, height = 48.dp)
            }
            note?.let { CoveText(it, style = CoveType.Meta, color = Cove.colors.muted) }
        } else {
            SaveAndTest(ui, vm, ServiceId.Google, drafts, "Sign in", { errors = it }, { note = it }, context)
            note?.let { CoveText(it, style = CoveType.Meta, color = Cove.colors.muted) }
        }
    }
}

@Composable
private fun GeminiSheet(ui: ConnectUi, vm: ConnectViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val main = listOf(CredentialField.GeminiApiKey)
    val advanced = listOf(CredentialField.GeminiModel, CredentialField.GeminiFallbackModel)
    var drafts by rememberDrafts(ui, main + advanced)
    var errors by remember { mutableStateOf(emptyMap<CredentialField, String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    ScrollingSheetContent(
        "Gemini", "Helps with trickier voice commands and writes the lines of your morning brief. Only short, non-journal text is ever sent.",
        onDismiss,
    ) {
        ui.ai?.let { AiStatusLines(it) }
        Steps(
            listOf(
                "Open Google AI Studio and sign in.",
                "Create an API key and copy it. New keys start with AQ. and older ones with AIza; both work.",
                "Paste it here and tap Test connection.",
                "The free plan is enough. Google may use free-plan prompts to improve its products; Cove only sends short non-journal text. If you turn on billing, set a budget alert.",
            ),
        )
        SheetAction("Open dashboard", { openLink(context, ConnectLinks.AI_STUDIO_KEYS) })
        FieldInputs(main, drafts, errors, context) { f, v -> drafts = drafts + (f to v) }
        Box(Modifier.pressable({ showAdvanced = !showAdvanced })) {
            CoveText(if (showAdvanced) "Hide advanced" else "Advanced: model", style = CoveType.MetaMedium, color = Cove.colors.muted)
        }
        if (showAdvanced) FieldInputs(advanced, drafts, errors, context) { f, v -> drafts = drafts + (f to v) }
        SaveAndTest(ui, vm, ServiceId.Gemini, drafts, "Test connection", { errors = it }, { note = it }, context)
        note?.let { CoveText(it, style = CoveType.Meta, color = Cove.colors.muted) }
    }
}

/** Save and Test buttons plus the result line for [service]. */
@Composable
private fun SaveAndTest(
    ui: ConnectUi,
    vm: ConnectViewModel,
    service: ServiceId,
    drafts: Map<CredentialField, String>,
    testLabel: String,
    onErrors: (Map<CredentialField, String>) -> Unit,
    onNote: (String?) -> Unit,
    context: Context,
) {
    val requester = remember { BringIntoViewRequester() }
    val result = ui.tests[service]
    LaunchedEffect(result, ui.busy) { if (result != null || ui.busy == service) requester.bringIntoView() }
    Box(Modifier.bringIntoViewRequester(requester)) { ResultLine(result, ui.busy == service) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PillButton(
            "Save", { val e = vm.save(drafts); onErrors(e); onNote(if (e.isEmpty()) "Saved." else null) },
            Modifier.weight(1f), kind = ButtonKind.Secondary, height = 48.dp,
        )
        PillButton(
            testLabel, { onNote(null); onErrors(vm.test(service, drafts, context)) },
            Modifier.weight(1f), height = 48.dp,
        )
    }
}
