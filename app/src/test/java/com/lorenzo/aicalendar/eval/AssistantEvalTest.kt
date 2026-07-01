package com.lorenzo.aicalendar.eval

import com.lorenzo.aicalendar.data.assistant.OpenRouterAssistant
import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import com.lorenzo.aicalendar.data.remote.openrouter.OPENROUTER_EVENT_MODELS
import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.assistant.AssistantAction
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantOperation
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.profile.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Properties

/**
 * Eval harness for the AI assistant: a suite of realistic Italian user requests with
 * verifiable expectations (action, dates, RRULE, op count), run against one or more
 * OpenRouter models in parallel and summarized in a comparison table.
 *
 * Opt-in: it runs only when an OpenRouter key is available (OPENROUTER_API_KEY env var or
 * `openrouter.devKey` in local.properties) — otherwise the test is SKIPPED, so CI is
 * unaffected. See docs/EVAL.md for the prompt-improvement loop built on top of this.
 *
 *   OPENROUTER_API_KEY=sk-or-... ./gradlew :app:testDebugUnitTest --tests '*AssistantEvalTest*'
 *   EVAL_MODELS="qwen/qwen3-next-80b-a3b-instruct:free" ...   # narrow to one model
 *   EVAL_MIN_PASS=12 ...                                      # fail if the best model drops below
 */
class AssistantEvalTest {

    private val zone = ZoneId.of("Europe/Rome")

    /** Fixed "now" (a Tuesday) so relative dates in the suite are deterministic. */
    private val nowLocal = LocalDateTime.of(2026, 7, 7, 8, 30)
    private val now = nowLocal.atZone(zone).toInstant()
    private val tomorrow: LocalDate = nowLocal.toLocalDate().plusDays(1) // Wed 8 Jul
    private val afterTomorrow: LocalDate = nowLocal.toLocalDate().plusDays(2) // Thu 9 Jul
    private val nextMonday: LocalDate = LocalDate.of(2026, 7, 13)

    @Test
    fun evalSuite() {
        val key = resolveKey()
        assumeTrue("Nessuna chiave OpenRouter (OPENROUTER_API_KEY o openrouter.devKey): eval saltata.", key != null)

        val models = System.getenv("EVAL_MODELS")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: OPENROUTER_EVENT_MODELS

        val assistant = buildAssistant(key!!)
        val cases = dataset()
        val semaphore = Semaphore(PARALLELISM)

        val results: Map<String, List<CaseResult>> = runBlocking {
            models.associateWith { model ->
                cases.map { case ->
                    async {
                        semaphore.withPermit { runCase(assistant, model, case) }
                    }
                }
            }.mapValues { (_, deferred) -> deferred.awaitAll() }
        }

        val report = buildReport(cases, models, results)
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/assistant-eval.txt").writeText(report)

        val minPass = System.getenv("EVAL_MIN_PASS")?.toIntOrNull()
        if (minPass != null) {
            val best = results.values.maxOf { list -> list.count { it.failures.isEmpty() } }
            check(best >= minPass) {
                "Il modello migliore ha superato solo $best/${cases.size} casi (soglia EVAL_MIN_PASS=$minPass)"
            }
        }
    }

    // ---------------------------------------------------------------- dataset

    private class EvalCase(
        val id: String,
        val message: String,
        val agenda: List<CalendarEvent> = emptyList(),
        val check: (AssistantReply) -> List<String>,
    )

    private class CaseResult(val caseId: String, val failures: List<String>)

    private fun dataset(): List<EvalCase> = listOf(
        EvalCase("pranzo-domani", "Pranzo con Anna domani alle 13") { r ->
            r.requireSingleCreate(startsAt = tomorrow.atTime(13, 0), recurrence = false)
        },
        EvalCase("dentista-dopodomani", "Dentista dopodomani alle 16:30") { r ->
            r.requireSingleCreate(startsAt = afterTomorrow.atTime(16, 30), recurrence = false)
        },
        EvalCase("riunione-luogo", "Riunione lunedì prossimo alle 10 in ufficio") { r ->
            r.requireSingleCreate(startsAt = nextMonday.atTime(10, 0), recurrence = false) +
                r.withOp { op ->
                    val hasPlace = op.draft?.location?.isNotBlank() == true ||
                        op.draft?.title?.contains("uffic", ignoreCase = true) == true
                    if (hasPlace) emptyList() else listOf("luogo 'ufficio' perso (né location né titolo)")
                }
        },
        EvalCase("palestra-lun-mer", "Palestra ogni lunedì e mercoledì alle 19") { r ->
            r.requireRrule("FREQ=WEEKLY", "MO", "WE")
        },
        EvalCase("visita-mensile", "Ricordami la visita medica un sabato al mese alle 9") { r ->
            r.requireRrule("FREQ=MONTHLY") + r.withOp { op ->
                val rrule = op.draft?.recurrence?.rrule.orEmpty()
                if (Regex("BYDAY=-?\\dSA").containsMatchIn(rrule)) emptyList()
                else listOf("manca l'ordinale mensile (atteso BYDAY=<n>SA, trovato '$rrule')")
            }
        },
        EvalCase("ultimo-venerdi", "Cena con i colleghi l'ultimo venerdì del mese alle 20:30") { r ->
            r.requireRrule("FREQ=MONTHLY", "BYDAY=-1FR")
        },
        EvalCase("quindici-del-mese", "Il 15 di ogni mese devo fare il bonifico dell'affitto alle 9") { r ->
            r.requireRrule("FREQ=MONTHLY", "BYMONTHDAY=15")
        },
        EvalCase(
            "routine-multi",
            "Lavoro dal lunedì al venerdì dalle 9 alle 18 e il sabato mattina studio dalle 9 alle 12",
        ) { r ->
            buildList {
                if (r.operations.size < 2) add("attese ≥2 operazioni per la routine, trovate ${r.operations.size}")
                val rrules = r.operations.mapNotNull { it.draft?.recurrence?.rrule }
                if (rrules.size != r.operations.size) add("non tutti gli eventi della routine hanno una ricorrenza")
                val work = rrules.any { rule -> listOf("MO", "TU", "WE", "TH", "FR").all { rule.contains(it) } }
                if (!work) add("nessun evento copre lun-ven (BYDAY=MO,TU,WE,TH,FR)")
                if (rrules.none { it.contains("SA") }) add("manca l'evento del sabato")
            }
        },
        EvalCase(
            "sposta-riunione",
            "Sposta la riunione di mercoledì alle 15",
            agenda = listOf(event("meet", "Riunione team", tomorrow.atTime(10, 0), tomorrow.atTime(11, 0))),
        ) { r ->
            r.withOp { op ->
                buildList {
                    if (op.action != AssistantAction.UPDATE) add("attesa action=UPDATE, trovata ${op.action}")
                    if (op.targetRef != 1) add("atteso ref=1, trovato ${op.targetRef}")
                    val start = op.draft?.start?.atZone(zone)?.toLocalDateTime()
                    if (start != tomorrow.atTime(15, 0)) add("atteso inizio ${tomorrow}T15:00, trovato $start")
                }
            }
        },
        EvalCase(
            "cancella-palestra",
            "Cancella la palestra di domani",
            agenda = listOf(event("gym", "Palestra", tomorrow.atTime(19, 0), tomorrow.atTime(20, 0))),
        ) { r ->
            r.withOp { op ->
                buildList {
                    if (op.action != AssistantAction.DELETE) add("attesa action=DELETE, trovata ${op.action}")
                    if (op.targetRef != 1) add("atteso ref=1, trovato ${op.targetRef}")
                }
            }
        },
        EvalCase(
            "domanda-agenda",
            "Cosa ho in agenda questa settimana?",
            agenda = listOf(event("meet", "Riunione team", tomorrow.atTime(10, 0), tomorrow.atTime(11, 0))),
        ) { r -> r.requireNoOps() },
        EvalCase("fuori-ambito", "Che tempo farà domani?") { r -> r.requireNoOps() },
        EvalCase("compleanno-allday", "Il compleanno di mamma è il 15 agosto, segnalo tutto il giorno") { r ->
            r.withOp { op ->
                buildList {
                    if (op.action != AssistantAction.CREATE) add("attesa action=CREATE, trovata ${op.action}")
                    if (op.draft?.allDay != true) add("atteso allDay=true")
                    val date = op.draft?.start?.atZone(zone)?.toLocalDate()
                    if (date != LocalDate.of(2026, 8, 15)) add("attesa data 2026-08-15, trovata $date")
                }
            }
        },
        EvalCase("corso-durata", "Corso di inglese ogni martedì dalle 18 alle 19") { r ->
            r.requireRrule("FREQ=WEEKLY", "TU") + r.withOp { op ->
                buildList {
                    val start = op.draft?.start?.atZone(zone)?.toLocalTime()
                    if (start != LocalTime.of(18, 0)) add("atteso inizio 18:00, trovato $start")
                    val end = op.draft?.end?.atZone(zone)?.toLocalTime()
                    if (end != LocalTime.of(19, 0)) add("attesa fine 19:00, trovata $end")
                }
            }
        },
        EvalCase("manca-orario", "Ricordami di chiamare il commercialista") { r ->
            // Missing the "when": the assistant should ask in the reply and create nothing.
            r.requireNoOps()
        },
        EvalCase(
            // Real regression from the phone: morning train to Torino, user asks lunch in Napoli
            // the same day. No time overlap, but spatially impossible: the assistant should ask
            // instead of silently creating.
            "conflitto-di-luogo",
            "Pranzo a Napoli con Francesco domani alle 13",
            agenda = listOf(
                event("train", "Treno per Torino PN", tomorrow.atTime(7, 40), tomorrow.atTime(12, 46)),
            ),
        ) { r -> r.requireNoOps() },
        EvalCase(
            // Another phone regression: "il 14 maggio" said in July was scheduled in the past.
            "data-passata",
            "Ricordami il 14 maggio di andare a pranzo a Roma con Ciaccio",
        ) { r ->
            // Asking for clarification (0 ops) is fine; creating is fine only in the future.
            r.operations.firstOrNull()?.let { op ->
                val start = op.draft?.start
                if (start != null && !start.isAfter(now)) {
                    listOf("data nel passato: ${start.atZone(zone).toLocalDate()} (adesso è ${nowLocal.toLocalDate()})")
                } else {
                    emptyList()
                }
            } ?: emptyList()
        },
    )

    // ---------------------------------------------------------------- check helpers

    private fun AssistantReply.requireNoOps(): List<String> =
        if (operations.isEmpty()) emptyList()
        else listOf("attese 0 operazioni, trovate ${operations.size}: ${operations.map { it.action }}")

    private fun AssistantReply.withOp(block: (AssistantOperation) -> List<String>): List<String> {
        val op = operations.firstOrNull()
            ?: return listOf("attesa almeno 1 operazione, trovate 0 (reply: ${text.take(80)})")
        return block(op)
    }

    private fun AssistantReply.requireSingleCreate(
        startsAt: LocalDateTime,
        recurrence: Boolean,
    ): List<String> = withOp { op ->
        buildList {
            if (operations.size != 1) add("attesa 1 operazione, trovate ${operations.size}")
            if (op.action != AssistantAction.CREATE) add("attesa action=CREATE, trovata ${op.action}")
            val start = op.draft?.start?.atZone(zone)?.toLocalDateTime()
            if (start != startsAt) add("atteso inizio $startsAt, trovato $start")
            if (!recurrence && op.draft?.recurrence != null) {
                add("ricorrenza inattesa: ${op.draft?.recurrence?.rrule}")
            }
        }
    }

    private fun AssistantReply.requireRrule(vararg fragments: String): List<String> = withOp { op ->
        val rrule = op.draft?.recurrence?.rrule
            ?: return@withOp listOf("attesa una ricorrenza, trovata nessuna")
        fragments.filterNot { rrule.contains(it, ignoreCase = true) }
            .map { "rrule '$rrule' non contiene '$it'" }
    }

    // ---------------------------------------------------------------- runner

    private suspend fun runCase(assistant: OpenRouterAssistant, model: String, case: EvalCase): CaseResult {
        val context = AssistantContext(
            now = now,
            zone = zone,
            profile = UserProfile(),
            upcomingEvents = case.agenda,
        )
        return try {
            val reply = assistant.respond(emptyList(), case.message, context, models = listOf(model))
            val failures = case.check(reply) + emojiCheck(reply)
            CaseResult(case.id, failures)
        } catch (e: Exception) {
            CaseResult(case.id, listOf("errore di chiamata: ${e.message?.take(120)}"))
        }
    }

    /** Cross-cutting rule: replies must never contain emoji/pictographs (user preference). */
    private fun emojiCheck(reply: AssistantReply): List<String> =
        if (EMOJI.containsMatchIn(reply.text)) listOf("il reply contiene emoji: '${reply.text.take(80)}'")
        else emptyList()

    private fun buildReport(
        cases: List<EvalCase>,
        models: List<String>,
        results: Map<String, List<CaseResult>>,
    ): String = buildString {
        appendLine()
        appendLine("════ Eval assistente — ${cases.size} casi × ${models.size} modelli ════")
        for (model in models) {
            val list = results.getValue(model)
            val passed = list.count { it.failures.isEmpty() }
            appendLine()
            appendLine("── $model — $passed/${cases.size} ──")
            for (result in list) {
                if (result.failures.isEmpty()) {
                    appendLine("  PASS ${result.caseId}")
                } else {
                    appendLine("  FAIL ${result.caseId}")
                    result.failures.forEach { appendLine("       · $it") }
                }
            }
        }
        appendLine()
        appendLine("── Riepilogo ──")
        val width = models.maxOf { it.length }
        for (model in models) {
            val passed = results.getValue(model).count { it.failures.isEmpty() }
            appendLine("  ${model.padEnd(width)}  $passed/${cases.size}")
        }
    }

    // ---------------------------------------------------------------- wiring

    private fun buildAssistant(key: String): OpenRouterAssistant {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
            encodeDefaults = true // response_format needs its "type" field serialized
        }
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
        val keyProvider = object : ApiKeyProvider {
            override suspend fun currentKey(): String = key
        }
        return OpenRouterAssistant(OpenRouterApi(client, keyProvider, json), json)
    }

    /** OPENROUTER_API_KEY env var, or `openrouter.devKey` from local.properties (walking up). */
    private fun resolveKey(): String? {
        System.getenv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val file = File(dir, "local.properties")
            if (file.exists()) {
                val props = Properties().apply { file.inputStream().use { load(it) } }
                return props.getProperty("openrouter.devKey")?.takeIf { it.isNotBlank() }
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun event(id: String, title: String, start: LocalDateTime, end: LocalDateTime) =
        CalendarEvent(
            id = id,
            title = title,
            start = start.atZone(zone).toInstant(),
            zone = zone,
            end = end.atZone(zone).toInstant(),
            createdAt = now,
            updatedAt = now,
        )

    private companion object {
        /** Free models hit per-minute rate limits: keep total in-flight calls modest. */
        const val PARALLELISM = 3

        /** Emoji/pictographs (surrogate pairs) plus common symbol pictograms like ⚠. */
        val EMOJI = Regex("[\\uD83C-\\uD83E\\u2600-\\u27BF\\u2B00-\\u2BFF]")
    }
}
