package com.sadique.dailyledger.data

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

data class OrganizerMemberDraft(val name: String, val phone: String, val whatsapp: String, val units: Int, val turn: Int) {
    fun validated() = copy(name = memberText(name, "Name", 200, true),
        phone = memberText(phone, "Phone", 80), whatsapp = memberText(whatsapp, "WhatsApp", 80))
    internal fun fields() = listOf(name, phone, whatsapp, units.toString(), turn.toString())
}

data class PersonalTurnDraft(val name: String, val phone: String, val whatsapp: String, val turn: Int,
    val month: String, val isMe: Boolean, val note: String, val halfUnits: Int) {
    fun validated() = copy(name = memberText(name.ifBlank { if (isMe) "Me" else "" }, "Name", 200, true),
        phone = memberText(phone, "Phone", 80), whatsapp = memberText(whatsapp, "WhatsApp", 80),
        note = memberText(note, "Note", 2000))
    internal fun fields() = listOf(name, phone, whatsapp, turn.toString(), month, isMe.toString(), note, halfUnits.toString())
}

private fun memberText(value: String, label: String, maxBytes: Int, required: Boolean = false): String {
    val text = value.trim()
    require(!required || text.isNotBlank()) { "$label is required." }
    require(text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "$label is too long." }
    return text
}

/** Identity is bound to the complete ordered request, not just individual rows.
 * Replays never overwrite later contact/turn/payment changes. No schema change is needed. */
internal class KametiBatch(owner: String, kind: String, requestId: String, circleId: String, rows: List<List<String>>) {
    val prefix: String
    val ids: List<String>
    init {
        require(rows.size in 1..240) { "Add between 1 and 240 rows." }
        require(UUID.fromString(requestId).toString() == requestId) { "Invalid save request." }
        prefix = "$owner:$kind-$requestId-"
        val digest = MessageDigest.getInstance("SHA-256")
        (listOf(circleId.substringAfter(':', circleId), rows.size.toString()) + rows.flatten()).forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        ids = rows.indices.map { "$prefix$hash-$it" }
    }
    fun alreadySaved(existingIds: List<String>): Boolean {
        val matches = existingIds.filter { it.startsWith(prefix) }
        if (matches.isEmpty()) return false
        require(matches.toSet() == ids.toSet()) { "This save request has changed or some saved rows were removed. Reopen the form and review the saved members." }
        return true
    }
}
