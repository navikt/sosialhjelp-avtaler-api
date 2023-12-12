package no.nav.sosialhjelp.avtaler

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test

@Serializable
data class Kommune(val organisasjonsnummer: String, val navn: String)

internal class ApplicationTest {
    @Test
    fun `test deserialization`(): Unit =
        runBlocking(Dispatchers.IO) {
            val kommuneJson =
                withContext(Dispatchers.IO) {
                    this::class.java.getResource("/enhetsregisteret/kommuner.json")!!.readText()
                }
            val json = Json { ignoreUnknownKeys = true }

            val kommuner = json.decodeFromString<Array<Kommune>>(kommuneJson)
            kommuner.size shouldBeGreaterThan 1
            kommuner.first { it.organisasjonsnummer == "210522352" }.navn shouldBeEqualIgnoringCase "ALSTAHAUG KOMMUNE"
        }
}
