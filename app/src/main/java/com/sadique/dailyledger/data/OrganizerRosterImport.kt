package com.sadique.dailyledger.data

/**
 * Parses spreadsheet paste (TSV) and CSV into organizer drafts. Parsing never writes money.
 * Expected columns: name, phone, whatsapp, share, turn. Header is optional.
 * A person's repeated rows are intentional multiple shares/turns, not duplicates.
 */
fun parseOrganizerRosterText(text: String, maxRows: Int = 120): List<OrganizerMemberDraft> {
    require(text.length <= 100_000) { "Import is too large." }
    val clean = text.removePrefix("\uFEFF")
    val delimiter = if (clean.lineSequence().firstOrNull()?.contains('\t') == true) '\t' else ','
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < clean.length) {
        val c = clean[i]
        when {
            c == '"' && quoted && i + 1 < clean.length && clean[i + 1] == '"' -> {
                cell.append('"'); i++
            }
            c == '"' -> {
                require(quoted || cell.isEmpty()) { "Unexpected quote near row ${rows.size + 1}." }
                quoted = !quoted
            }
            !quoted && c == delimiter -> { row.add(cell.toString()); cell.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                row.add(cell.toString()); cell.clear()
                if (row.any { it.isNotBlank() }) rows.add(row.toList())
                row.clear()
                if (c == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') i++
                require(rows.size <= maxRows + 1) { "Too many rows (limit $maxRows)." }
            }
            else -> cell.append(c)
        }
        i++
    }
    require(!quoted) { "Unclosed quoted CSV value." }
    row.add(cell.toString())
    if (row.any { it.isNotBlank() }) rows.add(row.toList())
    require(rows.isNotEmpty()) { "No member rows found." }
    val header = rows.first().firstOrNull()?.trim()?.lowercase() in setOf("name", "member", "member name")
    val data = if (header) rows.drop(1) else rows
    require(data.isNotEmpty() && data.size <= maxRows) { "Import must contain 1 to $maxRows members." }
    return data.mapIndexed { index, fields ->
        val line = index + if (header) 2 else 1
        require(fields.size in 1..5) { "Row $line: expected up to 5 columns." }
        val name = fields[0].trim()
        val phone = fields.getOrElse(1) { "" }.trim()
        val whatsapp = fields.getOrElse(2) { "" }.trim()
        val share = fields.getOrElse(3) { "1" }.trim().ifEmpty { "1" }
        val turn = fields.getOrElse(4) { "0" }.trim().ifEmpty { "0" }.toIntOrNull()
        val units = when (share.lowercase()) {
            "0.5", "half", "1/2" -> 1
            "1", "full" -> 2
            else -> null
        }
        require(name.isNotBlank() && name.length <= 120) { "Row $line: invalid member name." }
        require(phone.length <= 40 && whatsapp.length <= 40) { "Row $line: contact too long." }
        require(units != null && turn != null && turn >= 0) { "Row $line: invalid share or turn." }
        OrganizerMemberDraft(name, phone, whatsapp, units, turn)
    }
}

/** Validate imported rows against existing and queued shares before any insert. */
fun checkOrganizerImportCapacity(
    totalSlots: Int, existing: List<OrganizerMemberEntity>, drafts: List<OrganizerMemberDraft>,
) {
    require(totalSlots in 2..120 && drafts.isNotEmpty())
    val occupied = IntArray(totalSlots + 1)
    var used = 0
    existing.forEach {
        require(it.halfUnits in 1..2 && it.turnNumber in 0..totalSlots)
        used = Math.addExact(used, it.halfUnits)
        if (it.turnNumber != 0) occupied[it.turnNumber] += it.halfUnits
    }
    drafts.forEachIndexed { index, d ->
        require(d.name.isNotBlank() && d.units in 1..2 && d.turn in 0..totalSlots) {
            "Row ${index + 1}: invalid share or turn."
        }
        used = Math.addExact(used, d.units)
        require(used <= totalSlots * 2) { "Row ${index + 1}: Kameti shares exceed capacity." }
        if (d.turn != 0) {
            occupied[d.turn] += d.units
            require(occupied[d.turn] <= 2) { "Row ${index + 1}: turn #${d.turn} is full." }
        }
    }
}
