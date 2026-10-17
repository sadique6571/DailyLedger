package com.sadique.dailyledger.data

import java.time.LocalDate
import java.security.MessageDigest

/** Pure domain policy and previews. These objects are not persisted or authority grants. */
enum class KametiPaymentVerification { PENDING, VERIFIED, REJECTED, REVERSED }
enum class KametiRole { OWNER, COLLECTOR, APPROVER, VIEWER }
enum class KametiPermission { VIEW, COLLECT, VERIFY, APPROVE_PAYOUT, CORRECT, CLOSE }

fun kametiMay(role: KametiRole, action: KametiPermission): Boolean = when (role) {
    KametiRole.OWNER -> true
    KametiRole.COLLECTOR -> action in setOf(KametiPermission.VIEW, KametiPermission.COLLECT)
    KametiRole.APPROVER -> action in setOf(KametiPermission.VIEW, KametiPermission.VERIFY, KametiPermission.APPROVE_PAYOUT)
    KametiRole.VIEWER -> action == KametiPermission.VIEW
}

/** Explicit verification: unverified promises/transfers never become settled cash. */
data class KametiVerificationPreview(
    val verifiedMinor: Long,
    val pendingMinor: Long,
    val rejectedOrReversedMinor: Long,
)
data class KametiPaymentCandidate(val id: String, val amountMinor: Long, val state: KametiPaymentVerification)
fun kametiVerificationPreview(entries: List<KametiPaymentCandidate>): KametiVerificationPreview {
    require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate payment IDs." }
    var verified = 0L; var pending = 0L; var rejected = 0L
    entries.forEach { e ->
        require(e.id.isNotBlank() && e.amountMinor > 0L)
        when (e.state) {
            KametiPaymentVerification.VERIFIED -> verified = Math.addExact(verified, e.amountMinor)
            KametiPaymentVerification.PENDING -> pending = Math.addExact(pending, e.amountMinor)
            else -> rejected = Math.addExact(rejected, e.amountMinor)
        }
    }
    return KametiVerificationPreview(verified, pending, rejected)
}

/** A payment promise is a reminder, never a collection. */
data class KametiPaymentPromise(val memberId: String, val amountMinor: Long, val dueDate: LocalDate) {
    init { require(memberId.isNotBlank() && amountMinor > 0L) }
}
fun kametiPromiseOverdue(promise: KametiPaymentPromise, today: LocalDate): Boolean =
    today.isAfter(promise.dueDate)

/** Preview proportional family contributions; actual allocators must retain payer identities. */
data class KametiFamilyAllocation(val payerId: String, val memberId: String, val amountMinor: Long)
fun validateFamilyAllocations(
    allocations: List<KametiFamilyAllocation>,
    remainingByMember: Map<String, Long>,
): Long {
    require(allocations.isNotEmpty())
    val used = mutableMapOf<String, Long>()
    allocations.forEach { a ->
        require(a.payerId.isNotBlank() && a.memberId.isNotBlank() && a.amountMinor > 0)
        val outstanding = requireNotNull(remainingByMember[a.memberId]) { "Unknown member." }
        require(outstanding >= 0L)
        val updated = Math.addExact(used[a.memberId] ?: 0L, a.amountMinor)
        require(updated <= outstanding) { "Allocation exceeds unpaid balance." }
        used[a.memberId] = updated
    }
    return allocations.fold(0L) { sum, a -> Math.addExact(sum, a.amountMinor) }
}

/** A correction must explicitly point to its original event; never overwrite it. */
data class KametiCorrectionRequest(
    val originalEventId: String,
    val reason: String,
    val actorId: String,
    val replacementMinor: Long,
) {
    init {
        require(originalEventId.isNotBlank() && actorId.isNotBlank())
        require(reason.trim().length in 5..500)
        require(replacementMinor >= 0L)
    }
}

data class KametiExitPreview(
    val installmentsMinor: Long,
    val payoutsMinor: Long,
    val unsettledMinor: Long,
    val disputes: Int,
) {
    init {
        require(installmentsMinor >= 0 && payoutsMinor >= 0 && unsettledMinor >= 0 && disputes >= 0)
    }
    val needsReview: Boolean get() = unsettledMinor != 0L || disputes != 0
    val netContributionsMinor: Long get() = Math.subtractExact(installmentsMinor, payoutsMinor)
}

data class KametiHealthSnapshot(
    val dueMinor: Long,
    val verifiedMinor: Long,
    val pendingMinor: Long,
    val overdueMinor: Long,
    val payoutDueMinor: Long,
    val availablePooledMinor: Long,
) {
    init {
        require(listOf(dueMinor, verifiedMinor, pendingMinor, overdueMinor, payoutDueMinor, availablePooledMinor).all { it >= 0L })
        require(verifiedMinor <= dueMinor)
        require(overdueMinor <= dueMinor)
    }
    val collectionPercent: Int get() = if (dueMinor == 0L) 100 else ((verifiedMinor.toDouble() / dueMinor) * 100).toInt()
    val payoutShortfallMinor: Long get() = (payoutDueMinor - availablePooledMinor).coerceAtLeast(0L)
}

/** A recoverable export requires validation of manifest and payload, not just a file name. */
data class KametiRecoveryManifest(
    val ownerId: String,
    val schemaVersion: Int,
    val recordCount: Int,
    val sha256: String,
)
fun verifyKametiRecoveryManifest(manifest: KametiRecoveryManifest, owner: String, payload: ByteArray): Boolean {
    if (manifest.ownerId != owner || manifest.schemaVersion < 7 || manifest.recordCount < 0) return false
    val hash = MessageDigest.getInstance("SHA-256").digest(payload)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    return manifest.sha256.equals(hash, ignoreCase = true)
}

data class KametiTemplate(val name: String, val monthlyMinor: Long, val slots: Int, val graceDays: Int) {
    init { require(name.isNotBlank() && monthlyMinor > 0L && monthlyMinor % 2L == 0L && slots in 2..120 && graceDays in 0..31) }
}
/** Template returns configuration only and does not create members or money. */
fun kametiDefaultTemplate(kind: String): KametiTemplate = when (kind.lowercase()) {
    "family" -> KametiTemplate("Family Kameti", 500000, 12, 5)
    "office" -> KametiTemplate("Office Kameti", 500000, 12, 3)
    "friends" -> KametiTemplate("Friends Kameti", 500000, 20, 5)
    else -> throw IllegalArgumentException("Unknown template.")
}
