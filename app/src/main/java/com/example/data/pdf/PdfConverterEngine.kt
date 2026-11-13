package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Xml
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PdfConverterEngine(
    private val context: Context,
    private val creationEngine: PdfCreationEngine,
    private val rendererHelper: PdfRendererHelper
) {

    suspend fun pdfToImages(
        pdfFile: File,
        outputDir: File,
        asPng: Boolean = false,
        maxDimensionPx: Int = 1600
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!outputDir.exists()) outputDir.mkdirs()
            val pageCount = rendererHelper.getPageCount(pdfFile)
            require(pageCount > 0) { "PDF has no renderable pages (it may be encrypted or invalid)." }

            val ext = if (asPng) "png" else "jpg"
            val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (asPng) 100 else 92
            val outFiles = mutableListOf<File>()

            for (i in 0 until pageCount) {
                val bmp = rendererHelper.renderPage(pdfFile, i, maxDimensionPx)
                    ?: throw IllegalStateException("Failed to render page ${i + 1}.")
                try {
                    val out = File(outputDir, "${pdfFile.nameWithoutExtension}_page_${i + 1}.$ext")
                    FileOutputStream(out).use { fos ->
                        bmp.compress(format, quality, fos)
                    }
                    outFiles.add(out)
                } finally {
                    bmp.recycle()
                }
            }
            outFiles
        }
    }

    suspend fun packageFilesIntoZip(files: List<File>, zipFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    files.forEach { file ->
                        zos.putNextEntry(ZipEntry(file.name))
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                zipFile
            }
        }

    suspend fun docxToPdf(docxFile: File, outputPdf: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extractedText = extractDocxText(docxFile)
            require(extractedText.isNotBlank()) { "No readable text content found inside DOCX." }
            creationEngine.createFromText(
                outputFile = outputPdf,
                text = extractedText,
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.PORTRAIT,
                marginPt = 48f,
                fontSizePt = 12f,
                textColorArgb = Color.BLACK,
                headerText = docxFile.nameWithoutExtension,
                includePageNumbers = true
            ).getOrThrow()
        }
    }

    suspend fun xlsxToPdf(xlsxFile: File, outputPdf: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extractedText = extractXlsxText(xlsxFile)
            require(extractedText.isNotBlank()) { "No readable cell data found inside XLSX." }
            creationEngine.createFromText(
                outputFile = outputPdf,
                text = extractedText,
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.LANDSCAPE,
                marginPt = 36f,
                fontSizePt = 10.5f,
                textColorArgb = Color.BLACK,
                headerText = "Spreadsheet: ${xlsxFile.nameWithoutExtension}",
                includePageNumbers = true
            ).getOrThrow()
        }
    }

    suspend fun pptxToPdf(pptxFile: File, outputPdf: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extractedSlides = extractPptxText(pptxFile)
            require(extractedSlides.isNotBlank()) { "No readable slide text found inside PPTX." }
            creationEngine.createFromText(
                outputFile = outputPdf,
                text = extractedSlides,
                pageSize = PageSizeOption.A4,
                orientation = PageOrientation.LANDSCAPE,
                marginPt = 48f,
                fontSizePt = 13f,
                textColorArgb = Color.BLACK,
                headerText = "Presentation: ${pptxFile.nameWithoutExtension}",
                includePageNumbers = true
            ).getOrThrow()
        }
    }

    suspend fun pdfToDocxTextBased(pdfFile: File, outputDocx: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pagesText = extractPagesText(pdfFile)
                val bodyXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
                    pagesText.forEachIndexed { idx, text ->
                        append("<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Page ${idx + 1}</w:t></w:r></w:p>")
                        text.lines().forEach { line ->
                            val escaped = escapeXml(line)
                            append("""<w:p><w:r><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>""")
                        }
                    }
                    append("<w:sectPr/></w:body></w:document>")
                }

                ZipOutputStream(FileOutputStream(outputDocx)).use { zos ->
                    writeZipTextEntry(
                        zos,
                        "[Content_Types].xml",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
                    )
                    writeZipTextEntry(
                        zos,
                        "_rels/.rels",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
                    )
                    writeZipTextEntry(zos, "word/document.xml", bodyXml)
                }
                outputDocx
            }
        }

    suspend fun pdfToXlsxTextBased(pdfFile: File, outputXlsx: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pagesText = extractPagesText(pdfFile)
                val sheetXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                    var rowNum = 1
                    pagesText.forEachIndexed { pageIdx, content ->
                        append("""<row r="$rowNum"><c r="A$rowNum" t="inlineStr"><is><t>Page ${pageIdx + 1}</t></is></c></row>""")
                        rowNum++
                        content.lines().filter { it.isNotBlank() }.forEach { line ->
                            val cols = line.split(Regex("\\s{2,}|\\t")).filter { it.isNotBlank() }
                            append("""<row r="$rowNum">""")
                            cols.take(12).forEachIndexed { colIdx, cellText ->
                                val colLetter = ('A' + colIdx)
                                append("""<c r="$colLetter$rowNum" t="inlineStr"><is><t>${escapeXml(cellText)}</t></is></c>""")
                            }
                            append("</row>")
                            rowNum++
                        }
                    }
                    append("</sheetData></worksheet>")
                }

                ZipOutputStream(FileOutputStream(outputXlsx)).use { zos ->
                    writeZipTextEntry(
                        zos,
                        "[Content_Types].xml",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
                    )
                    writeZipTextEntry(
                        zos,
                        "_rels/.rels",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
                    )
                    writeZipTextEntry(
                        zos,
                        "xl/workbook.xml",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Extracted PDF" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
                    )
                    writeZipTextEntry(
                        zos,
                        "xl/_rels/workbook.xml.rels",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
                    )
                    writeZipTextEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
                }
                outputXlsx
            }
        }

    suspend fun pdfToPptxSlideImageBased(pdfFile: File, outputPptx: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pageCount = rendererHelper.getPageCount(pdfFile)
                require(pageCount > 0) { "No pages available to export." }
                val slideCount = minOf(pageCount, 40)

                ZipOutputStream(FileOutputStream(outputPptx)).use { zos ->
                    val contentTypes = StringBuilder(
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>"""
                    )
                    val presRels = StringBuilder(
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""
                    )
                    val sldIdLst = StringBuilder("<p:sldIdLst>")

                    for (i in 1..slideCount) {
                        contentTypes.append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                        presRels.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
                        sldIdLst.append("""<p:sldId id="${255 + i}" r:id="rId$i"/>""")

                        val bmp = rendererHelper.renderPage(pdfFile, i - 1, 1280)
                            ?: throw IllegalStateException("Failed rendering page $i")
                        val imgBytes = ByteArrayOutputStream().use { baos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                            bmp.recycle()
                            baos.toByteArray()
                        }
                        zos.putNextEntry(ZipEntry("ppt/media/slide_$i.jpeg"))
                        zos.write(imgBytes)
                        zos.closeEntry()

                        writeZipTextEntry(
                            zos,
                            "ppt/slides/_rels/slide$i.xml.rels",
                            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/slide_$i.jpeg"/>
</Relationships>"""
                        )
                        writeZipTextEntry(
                            zos,
                            "ppt/slides/slide$i.xml",
                            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
    <p:grpSpPr/>
    <p:pic>
      <p:nvPicPr><p:cNvPr id="2" name="Page $i"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
      <p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
      <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="9144000" cy="6858000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
    </p:pic>
  </p:spTree></p:cSld>
</p:sld>"""
                        )
                    }
                    contentTypes.append("</Types>")
                    presRels.append("</Relationships>")
                    sldIdLst.append("</p:sldIdLst>")

                    writeZipTextEntry(zos, "[Content_Types].xml", contentTypes.toString())
                    writeZipTextEntry(
                        zos,
                        "_rels/.rels",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
                    )
                    writeZipTextEntry(zos, "ppt/_rels/presentation.xml.rels", presRels.toString())
                    writeZipTextEntry(
                        zos,
                        "ppt/presentation.xml",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  $sldIdLst
  <p:sldSz cx="9144000" cy="6858000" type="screen4x3"/>
</p:presentation>"""
                    )
                }
                outputPptx
            }
        }

    private fun extractPagesText(pdfFile: File): List<String> {
        PDDocument.load(pdfFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper()
            val list = mutableListOf<String>()
            for (p in 1..doc.numberOfPages) {
                stripper.startPage = p
                stripper.endPage = p
                list.add(stripper.getText(doc))
            }
            return list
        }
    }

    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    val parser = Xml.newPullParser()
                    parser.setInput(xml.reader())
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "p" || parser.name.endsWith(":p")) {
                                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                                }
                            }
                            XmlPullParser.TEXT -> sb.append(parser.text)
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun extractXlsxText(file: File): String {
        val sharedStrings = mutableListOf<String>()
        val sheetEntries = mutableListOf<Pair<String, String>>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        val parser = Xml.newPullParser()
                        parser.setInput(xml.reader())
                        var current = StringBuilder()
                        var inSi = false
                        var ev = parser.eventType
                        while (ev != XmlPullParser.END_DOCUMENT) {
                            if (ev == XmlPullParser.START_TAG && parser.name == "si") {
                                inSi = true
                                current = StringBuilder()
                            } else if (ev == XmlPullParser.TEXT && inSi) {
                                current.append(parser.text)
                            } else if (ev == XmlPullParser.END_TAG && parser.name == "si") {
                                sharedStrings.add(current.toString())
                                inSi = false
                            }
                            ev = parser.next()
                        }
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                        sheetEntries.add(entry.name to zis.readBytes().toString(Charsets.UTF_8))
                    }
                }
                entry = zis.nextEntry
            }
        }

        val out = StringBuilder()
        sheetEntries.sortedBy { it.first }.forEachIndexed { idx, (_, xml) ->
            out.append("--- Sheet ${idx + 1} ---\n")
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var cellType = ""
            var inVal = false
            val rowCells = mutableListOf<String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> rowCells.clear()
                        "c" -> cellType = parser.getAttributeValue(null, "t").orEmpty()
                        "v", "t" -> inVal = true
                    }
                    XmlPullParser.TEXT -> if (inVal) {
                        val raw = parser.text
                        val value = if (cellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                        } else raw
                        rowCells.add(value)
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v", "t" -> inVal = false
                        "row" -> if (rowCells.isNotEmpty()) out.append(rowCells.joinToString("  |  ")).append("\n")
                    }
                }
                ev = parser.next()
            }
            out.append("\n")
        }
        return out.toString().trim()
    }

    private fun extractPptxText(file: File): String {
        val slides = mutableListOf<Pair<Int, String>>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val match = Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry.name)
                if (match != null) {
                    val slideNum = match.groupValues[1].toIntOrNull() ?: 1
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    val sb = StringBuilder()
                    val parser = Xml.newPullParser()
                    parser.setInput(xml.reader())
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.START_TAG && (parser.name == "p" || parser.name.endsWith(":p")) && sb.isNotEmpty() && !sb.endsWith("\n")) {
                            sb.append("\n")
                        } else if (ev == XmlPullParser.TEXT) {
                            sb.append(parser.text)
                        }
                        ev = parser.next()
                    }
                    slides.add(slideNum to sb.toString().trim())
                }
                entry = zis.nextEntry
            }
        }
        return slides.sortedBy { it.first }.joinToString("\n\n") { (num, body) ->
            "Slide $num\n$body"
        }
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
