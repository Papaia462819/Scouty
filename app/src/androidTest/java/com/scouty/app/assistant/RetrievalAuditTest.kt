package com.scouty.app.assistant

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.assistant.domain.AssistantRepository
import com.scouty.app.assistant.model.AssistantConversationState
import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Retrieval-quality audit for the DETERMINISTIC path (Qwen disabled,
 * allowLocalModel = false). Runs a battery of representative Romanian queries
 * through the real on-device pipeline (FTS + embeddings + jina rerank) and logs,
 * per query, which card won (citations) and the answer summary.
 *
 * This is an observability harness, not a pass/fail gate: it always asserts
 * trivially true and the signal is in the logcat output (tag RETRIEVAL_AUDIT).
 * Read results with:
 *   adb logcat -s RETRIEVAL_AUDIT:D
 */
@RunWith(AndroidJUnit4::class)
class RetrievalAuditTest {

    private val context = DeviceContextSnapshot(
        batteryPercent = 80,
        batterySafe = true,
        isOnline = false,
        gpsFixed = false,
        localeTag = "ro-RO"
    )

    // Single-turn queries: (label, query, what a correct top card looks like).
    private val singleTurn = listOf(
        // --- base-case / generic (hub territory) ---
        Q("base_fire", "cum fac focul", "foc de bază / hub"),
        Q("base_water", "cum fac rost de apa", "găsire/purificare apă"),
        Q("base_shelter", "cum imi fac un adapost", "adăpost improvizat"),
        Q("base_gear_day", "ce echipament imi trebuie pentru o tura de o zi", "rucsac/echipament zi"),
        Q("base_food", "ce mananc pe munte", "mâncare pe traseu"),
        Q("base_dress_winter", "cum ma imbrac iarna pe creasta", "stratificare/iarnă"),
        Q("base_navigate", "cum ma orientez pe munte", "orientare/busolă"),
        Q("base_lost", "m-am ratacit ce fac", "primii pași când ești pierdut"),
        Q("base_pack", "cum imi organizez rucsacul", "organizare rucsac"),
        Q("base_plan", "cum imi planific o tura", "planificare tură"),

        // --- specific (must beat any generic hub) ---
        Q("spec_fire_nolighter", "cum aprind focul fara bricheta", "foc fără brichetă"),
        Q("spec_fire_wet", "cum aprind focul cu lemne ude", "lemne ude"),
        Q("spec_fire_extinguish", "cum sting corect focul", "stingere foc"),
        Q("spec_water_filter", "cum filtrez apa de izvor", "purificare apă"),
        Q("spec_marker", "ce inseamna marcajul cu banda rosie", "marcaje traseu"),
        Q("spec_fagaras", "cat de greu e traseul pe creasta fagaras", "Făgăraș dificultate"),
        Q("spec_bear", "am vazut un urs ce fac", "întâlnire urs"),
        Q("spec_blisters", "mi-au iesit basici la picioare", "bășici"),

        // --- typos / colloquial ---
        Q("typo_fire", "kum fak focu", "foc (cu typo)"),
        Q("qmark_with", "cum fac focul?", "foc — cu semn întrebare"),
        Q("qmark_without", "cum fac focul", "foc — fără semn întrebare"),
        Q("typo_fire_heavy", "cum aprnid focul fara brichta", "foc fără brichetă (typo dur)"),
        Q("typo_water", "cum filtez apa de izvor", "purificare apă (typo)"),
        Q("typo_gear", "ce echpament iau pe munte", "echipament (typo)"),
        Q("typo_entorsa", "am o entorsa la glezna ce fac", "entorsă/gleznă"),
        Q("typo_entorsa2", "ma doare glzna am o entrsa", "entorsă (typo)"),
        Q("colloq_cold", "mi-e frig tare ce fac", "frig/hipotermie"),

        // --- ambiguous single words ---
        Q("amb_apa", "apa", "apă (ambiguu)"),
        Q("amb_foc", "foc", "foc (ambiguu)"),
        Q("amb_frig", "frig", "frig (ambiguu)"),

        // --- elliptical WITHOUT context (expected weak: Qwen-interpreter territory) ---
        Q("ellip_rain_noctx", "si daca ploua?", "ELIPTIC fără context"),
        Q("ellip_winter_noctx", "dar iarna?", "ELIPTIC fără context"),
        Q("ellip_pronoun_noctx", "cum fac pe el?", "PRONUME fără context")
    )

    // Multi-turn chains: each is a list of user turns; we log what each turn retrieves.
    // These probe coreference / elliptical follow-ups that NEED prior context.
    private val multiTurn = listOf(
        Chain(
            "chain_fire_rain",
            listOf("cum fac focul fara bricheta", "si daca ploua?")
        ),
        Chain(
            "chain_fire_wind",
            listOf("cum aprind focul", "dar daca bate vantul?")
        ),
        Chain(
            "chain_water_winter",
            listOf("cum fac rost de apa", "si iarna cand totul e inghetat?")
        ),
        Chain(
            "chain_gear_pronoun",
            listOf("ce bocanci sunt buni de munte", "si cum ii usuc daca se uda?")
        ),
        Chain(
            "chain_fire_rain_wind",
            listOf("cum aprind focul fara bricheta", "dar daca ploua?", "si daca bate vantul?")
        ),
        Chain(
            "chain_bear_newsubject",
            listOf("cum fac focul", "ce fac daca vad un urs?")
        ),
        Chain(
            "chain_water_izvor",
            listOf("cum filtrez apa", "si daca e de la izvor?")
        ),
        Chain(
            "chain_followup_withq",
            listOf("cum fac focul fara bricheta", "si daca ploua?")
        ),
        Chain(
            "chain_followup_noq",
            listOf("cum fac focul fara bricheta", "si daca ploua")
        ),
        Chain(
            "chain_repro_rain",
            listOf("cum fac focu", "si daca a plouat", "si daca lemnele sunt ude")
        )
    )

    @Test
    fun auditDeterministicRetrieval() = runBlocking<Unit> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AssistantRepository(ctx)

        Log.d(TAG, "=== SINGLE-TURN (deterministic, allowLocalModel=false) ===")
        for (q in singleTurn) {
            val response = repository.answer(
                query = q.query,
                context = context,
                allowLocalModel = false
            )
            val cites = response.citations.joinToString(" || ") { "${it.sourceTitle}#${it.sectionTitle}" }
            Log.d(
                TAG,
                "[${q.label}] q=\"${q.query}\" expect={${q.expect}} " +
                    "mode=${response.generationMode} reasoning=${response.reasoningType} " +
                    "cites=[$cites] summary=\"${oneLine(response.answerText)}\""
            )
        }

        Log.d(TAG, "=== MULTI-TURN (coreference / elliptical follow-ups) ===")
        for (chain in multiTurn) {
            var state = AssistantConversationState()
            chain.turns.forEachIndexed { index, turn ->
                val response = repository.answer(
                    query = turn,
                    context = context,
                    conversationState = state,
                    allowLocalModel = false
                )
                state = response.conversationState
                val cites = response.citations.joinToString(" || ") { "${it.sourceTitle}#${it.sectionTitle}" }
                Log.d(
                    TAG,
                    "[${chain.label}#turn$index] q=\"$turn\" " +
                        "cites=[$cites] summary=\"${oneLine(response.answerText)}\""
                )
            }
        }
        Log.d(TAG, "=== AUDIT COMPLETE ===")
    }

    private fun oneLine(text: String): String =
        text.replace("\n", " \\n ").trim().take(280)

    private data class Q(val label: String, val query: String, val expect: String)
    private data class Chain(val label: String, val turns: List<String>)

    private companion object {
        private const val TAG = "RETRIEVAL_AUDIT"
    }
}
