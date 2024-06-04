package no.nav.sosialhjelp.avtaler.avtalemaler

import mu.KotlinLogging
import org.apache.poi.xwpf.usermodel.PositionInParagraph
import org.apache.poi.xwpf.usermodel.TextSegment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.xmlbeans.XmlCursor
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTProofErr
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText
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

    private fun XWPFParagraph.searchText2(
        searched: String,
        startPos: PositionInParagraph,
    ): TextSegment? {
        val startRun = startPos.run
        val startText = startPos.text
        val startChar = startPos.char
        var beginRunPos = 0
        var candCharPos = 0
        var newList = false

        // CTR[] rArray = paragraph.getRArray(); //This does not contain all runs. It lacks hyperlink runs for ex.
        val runs = runs

        var beginTextPos = 0
        var beginCharPos = 0 // must be outside the for loop

        // for (int runPos = startRun; runPos < rArray.length; runPos++) {
        for (runPos in startRun until runs.size) {
            // int beginTextPos = 0, beginCharPos = 0, textPos = 0, charPos; //int beginTextPos = 0, beginCharPos = 0 must be outside the for loop
            var textPos = 0
            var charPos: Int
            // CTR ctRun = rArray[runPos];
            val ctRun: CTR = runs[runPos].ctr
            val c: XmlCursor = ctRun.newCursor()
            c.selectPath("./*")
            try {
                while (c.toNextSelection()) {
                    when (val o = c.getObject()) {
                        is CTText -> {
                            if (textPos >= startText) {
                                val candidate: String = o.stringValue
                                charPos =
                                    if (runPos == startRun) {
                                        startChar
                                    } else {
                                        0
                                    }

                                while (charPos < candidate.length) {
                                    if ((candidate[charPos] == searched[0]) && (candCharPos == 0)) {
                                        beginTextPos = textPos
                                        beginCharPos = charPos
                                        beginRunPos = runPos
                                        newList = true
                                    }
                                    if (candidate[charPos] == searched[candCharPos]) {
                                        if (candCharPos + 1 < searched.length) {
                                            candCharPos++
                                        } else if (newList) {
                                            val segment = TextSegment()
                                            segment.beginRun = beginRunPos
                                            segment.beginText = beginTextPos
                                            segment.beginChar = beginCharPos
                                            segment.endRun = runPos
                                            segment.endText = textPos
                                            segment.endChar = charPos
                                            return segment
                                        }
                                    } else {
                                        candCharPos = 0
                                    }
                                    charPos++
                                }
                            }
                            textPos++
                        }

                        is CTProofErr -> {
                            c.removeXml()
                        }

                        is CTRPr -> {
                            // do nothing
                        }

                        else -> {
                            candCharPos = 0
                        }
                    }
                }
            } finally {
                c.dispose()
            }
        }
        return null
    }
}
