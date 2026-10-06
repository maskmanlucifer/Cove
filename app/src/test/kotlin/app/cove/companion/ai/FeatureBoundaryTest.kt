package app.cove.companion.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the facade honest: outside the `ai` package (and the wiring in `AppContainer`) no source may import ML Kit,
 * Gemini or any `ai.provider`, `ai.prompt`, `ai.schema`, router or policy type. Features see only [AiService] and `ai.model`.
 */
class FeatureBoundaryTest {
    private val root = listOf("src/main/kotlin/app/cove/companion", "app/src/main/kotlin/app/cove/companion").map(::File).first { it.isDirectory }
    private val banned = Regex(
        """^import (com\.google\.mlkit|com\.google\.ai|app\.cove\.companion\.ai\.(provider|prompt|schema|AiRouter|AiPolicy|AiProviders|DefaultAiService)).*""",
        RegexOption.MULTILINE,
    )

    @Test fun featureCodeOnlyUsesTheFacade() {
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.startsWith("ai/") || it.name == "AppContainer.kt" }
            .flatMap { f -> banned.findAll(f.readText()).map { "${f.relativeTo(root)}: ${it.value}" } }
            .toList()
        assertTrue("Import AiService instead:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test fun geminiAndMlKitNamesStayInsideTheAiPackage() {
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filterNot { it.relativeTo(root).path.startsWith("ai/") || it.name == "AppContainer.kt" }
            .filter { Regex("""GeminiDirectClient|GenerativeModel|SpeechRecognizerResponse""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }.toList()
        assertTrue(offenders.toString(), offenders.isEmpty())
    }
}
