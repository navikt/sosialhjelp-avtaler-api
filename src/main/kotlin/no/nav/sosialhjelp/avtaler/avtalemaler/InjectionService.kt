package no.nav.sosialhjelp.avtaler.avtalemaler

import mu.KotlinLogging
import org.apache.poi.xwpf.usermodel.PositionInParagraph
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.io.InputStream
import java.io.OutputStream

val log = KotlinLogging.logger {}

class InjectionService {
    fun injectReplacements(
        replacementMap: Map<String, String>,
        document: InputStream,
        outputStream: OutputStream,
    ) {
        document.use { inputStream ->
            XWPFDocument(inputStream).use { doc ->
                doc.paragraphs.onEach {
                    replacementMap.onEach { (searchText, replacementText) ->
                        it.replaceTextSegment(searchText, replacementText)
                    }
                }
                doc.write(outputStream)
            }
        }
    }

    private fun XWPFParagraph.replaceTextSegment(
        searchText: String,
        replaceText: String,
    ) {
        val startPos = PositionInParagraph(0, 0, 0)
        var foundTextSegment = searchText(searchText, startPos)
        while (foundTextSegment != null) {
            val beginRun = runs[foundTextSegment.beginRun]
            var textInBeginRun = beginRun.getText(foundTextSegment.beginText)
            val textBefore = textInBeginRun.substring(0, foundTextSegment.beginChar)

            val endRun = runs[foundTextSegment.endRun]
            val textInEndRun = endRun.getText(foundTextSegment.endText)
            val textAfter = textInEndRun.substring(foundTextSegment.endChar + 1)

            if (foundTextSegment.endRun == foundTextSegment.beginRun) {
                textInBeginRun = textBefore + replaceText + textAfter
            } else {
                textInBeginRun = textBefore + replaceText
                endRun.setText(textAfter, foundTextSegment.endText)
            }

            beginRun.setText(textInBeginRun, foundTextSegment.beginText)

            // runs between begin run and end run needs to be removed
            for (runBetween in foundTextSegment.endRun - 1 downTo foundTextSegment.beginRun) {
                removeRun(runBetween)
            }
            foundTextSegment = searchText(searchText, startPos)
        }
    }
}
