package com.undy.tdaid.data.local

/** One row of a TD-supplied bio-import CSV: pdga_number,additional_bio_text. */
data class BioImportRow(val pdgaNumber: String, val additionalBio: String)

/** Parses a two-column CSV of PDGA number + free-text bio addition. No header row is expected,
 *  but if one is present it's skipped harmlessly — a first column that isn't all digits can't be
 *  a real PDGA number, so it's treated as a header/junk line rather than an error. */
object BioCsvParser {
    fun parse(csv: String): List<BioImportRow> = csv.lineSequence()
        .mapNotNull { parseLine(it) }
        .toList()

    private fun parseLine(line: String): BioImportRow? {
        if (line.isBlank()) return null
        val fields = splitCsvLine(line)
        val pdgaNumber = fields.getOrNull(0)?.trim()
        val bio = fields.getOrNull(1)?.trim()
        if (pdgaNumber.isNullOrEmpty() || bio.isNullOrEmpty()) return null
        if (!pdgaNumber.all { it.isDigit() }) return null
        return BioImportRow(pdgaNumber, bio)
    }

    /** Minimal RFC-4180 split: handles fields quoted with "..." (commas inside stay literal, ""
     *  is an escaped quote) since free-text bio entries commonly contain commas. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
