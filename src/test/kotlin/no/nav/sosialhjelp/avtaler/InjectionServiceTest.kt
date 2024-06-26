package no.nav.sosialhjelp.avtaler

import no.nav.sosialhjelp.avtaler.avtalemaler.InjectionService
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal class InjectionServiceTest {
    @Test
    fun `test injectReplacements`() {
        val pdfService = InjectionService()
        val out = BufferedOutputStream(FileOutputStream(File("src/test/resources/avtaler/result/result.docx")))
        out.use {
            pdfService.injectReplacements(
                mapOf("[navn]" to "Martin"),
                FileInputStream("src/test/resources/avtaler/Databehandleravtale_v.1.0.docx"),
                it,
            )
        }
    }
}
