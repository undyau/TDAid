package com.undy.tdaid.data.local

/** One row of a TD-supplied CSV: pdga_number,sponsor,walk_on_song. Either sponsor or walkOnSong
 *  may be blank — a row just needs a valid PDGA number and at least one of the two to be worth
 *  importing. */
data class BioImportRow(val pdgaNumber: String, val sponsor: String, val walkOnSong: String)

/** Parses a three-column CSV of PDGA number + sponsor + walk-on song. No header row is expected,
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
        val sponsor = fields.getOrNull(1)?.trim().orEmpty()
        val walkOnSong = fields.getOrNull(2)?.trim().orEmpty()
        if (pdgaNumber.isNullOrEmpty()) return null
        if (!pdgaNumber.all { it.isDigit() }) return null
        if (sponsor.isEmpty() && walkOnSong.isEmpty()) return null
        return BioImportRow(pdgaNumber, sponsor, walkOnSong)
    }

    /** Minimal RFC-4180 split: handles fields quoted with "..." (commas inside stay literal, ""
     *  is an escaped quote) since a walk-on song title commonly contains commas. */
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
