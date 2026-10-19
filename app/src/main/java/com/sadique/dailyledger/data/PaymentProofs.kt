package com.sadique.dailyledger.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sadique.dailyledger.auth.FirebaseRuntime
import com.sadique.dailyledger.sync.BackupLock
import com.sadique.dailyledger.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

internal const val MAX_PROOF_BYTES = 10 * 1024 * 1024
internal const val MAX_ALL_PROOF_BYTES = 20L * 1024 * 1024

/** Inspect the actual bytes instead of trusting a content-provider MIME label. */
internal fun proofMime(bytes: ByteArray): String {
    require(bytes.isNotEmpty() && bytes.size<=MAX_PROOF_BYTES) { "Choose a proof smaller than 10 MB." }
    fun prefix(vararg values:Int)=bytes.size>=values.size && values.indices.all{(bytes[it].toInt() and 255)==values[it]}
    return when {
        prefix(0x25,0x50,0x44,0x46,0x2d) -> "application/pdf"
        prefix(0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) -> "image/png"
        prefix(0xff,0xd8,0xff) -> "image/jpeg"
        bytes.size>=12 && String(bytes,0,4,Charsets.US_ASCII)=="RIFF" && String(bytes,8,4,Charsets.US_ASCII)=="WEBP" -> "image/webp"
        else -> error("Choose a JPEG, PNG, WebP photo or PDF.")
    }
}
internal fun proofBytes(proof:PaymentProofEntity):ByteArray {
    require(proof.data.length <= ((MAX_PROOF_BYTES+2)/3)*4 && proof.sizeBytes in 1..MAX_PROOF_BYTES.toLong()) { "Invalid proof size." }
    val bytes=Base64.getDecoder().decode(proof.data)
    require(bytes.size.toLong()==proof.sizeBytes && proofMime(bytes)==proof.mimeType) { "Invalid proof content." }
    return bytes
}
internal suspend fun LedgerRepository.proofParentExists(kind:String,id:String):Boolean {
    val d=database.ledgerDao()
    return when(kind) {
        "loan" -> d.loanPaymentsNow(ownerId).any{it.id==id}
        "credit" -> d.creditPaymentsNow(ownerId).any{it.id==id}
        "committee" -> d.committeePaymentsNow(ownerId).any{it.id==id}
        "committee-receipt" -> d.committeeReceiptsNow(ownerId).any{it.id==id}
        "organizer" -> d.organizerCollectionsNow(ownerId).any{it.id==id}
        "organizer-payout" -> d.organizerPayoutsNow(ownerId).any{it.id==id}
        else -> false
    }
}
internal suspend fun LedgerRepository.validateProof(proof:PaymentProofEntity) {
    require(proof.ownerId==ownerId && proof.id.isNotBlank() && proof.id.length<=200 && proof.createdAt>=0) { "Invalid payment proof." }
    require(proofParentExists(proof.kind,proof.paymentId)) { "The payment no longer exists in this account." }
    proofBytes(proof)
}
internal suspend fun LedgerRepository.readProofData(proof:PaymentProofEntity):PaymentProofEntity {
    val rows=database.ledgerDao().proofChunks(ownerId,proof.id)
    require(rows.isNotEmpty() && rows.size<=28 && rows.all{it.data.length<=512*1024} && rows.map{it.part}==rows.indices.toList()) { "Proof content is incomplete." }
    return proof.copy(data=rows.joinToString(""){it.data}).also{proofBytes(it)}
}
internal suspend fun LedgerRepository.persistProofs(proofs:List<PaymentProofEntity>) {
    val d=database.ledgerDao()
    d.insertPaymentProofs(proofs.map{it.copy(data="")})
    proofs.forEach { proof ->
        d.insertProofChunks(proof.data.chunked(512*1024).mapIndexed{index,chunk->PaymentProofChunkEntity(proof.id,index,ownerId,chunk)})
    }
}
internal suspend fun LedgerRepository.validProofs()=database.ledgerDao().paymentProofsNow(ownerId).filter{proofParentExists(it.kind,it.paymentId)}.map{readProofData(it)}
internal suspend fun LedgerRepository.pruneProofs() {
    database.ledgerDao().paymentProofsNow(ownerId).filterNot{proofParentExists(it.kind,it.paymentId)}.forEach{database.ledgerDao().deletePaymentProof(ownerId,it.id);database.ledgerDao().deleteProofChunks(ownerId,it.id)}
}
internal suspend fun LedgerRepository.savePaymentProofData(paymentId:String,kind:String,bytes:ByteArray):String=database.withTransaction {
    require(proofParentExists(kind,paymentId)) { "This payment was removed. Your proof was not saved." }
    val mime=proofMime(bytes);pruneProofs()
    val d=database.ledgerDao()
    require((d.proofBytes(ownerId)?:0)+bytes.size<=MAX_ALL_PROOF_BYTES && d.paymentProofsNow(ownerId).size<200) { "Payment proofs are limited to 20 MB per account. Remove unneeded attachments first." }
    val id="$ownerId:${UUID.randomUUID()}"
    persistProofs(listOf(PaymentProofEntity(id,ownerId,paymentId,kind,mime,Base64.getEncoder().encodeToString(bytes),bytes.size.toLong(),System.currentTimeMillis())))
    id
}
fun LedgerRepository.observePaymentProofs(paymentId:String)=database.ledgerDao().observePaymentProofs(ownerId,paymentId)
private fun LedgerRepository.checkProofAccount(context:Context) {
    check(FirebaseRuntime.auth(context).currentUser?.uid==ownerId) { "Please sign in again before changing proofs." }
}
suspend fun LedgerRepository.addPaymentProof(paymentId:String,kind:String,uri:Uri,mimeType:String?,context:Context)=withContext(Dispatchers.IO) {
    checkProofAccount(context)
    val bytes=context.contentResolver.openInputStream(uri)?.use{input ->
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_PROOF_BYTES){"Choose a proof smaller than 10 MB."};out.write(buffer,0,n)}
        out.toByteArray()
    }?:error("Could not read the selected proof.")
    BackupLock.mutex.withLock {
        checkProofAccount(context);SettingsStore(context).markDrivePending(ownerId)
        savePaymentProofData(paymentId,kind,bytes)
    }
    runCatching{SyncScheduler.syncNow(context,ownerId)}
    Unit
}
suspend fun LedgerRepository.deletePaymentProof(proof:PaymentProofEntity,context:Context)=withContext(Dispatchers.IO) {
    BackupLock.mutex.withLock {
        checkProofAccount(context);require(proof.ownerId==ownerId)
        SettingsStore(context).markDrivePending(ownerId)
        database.withTransaction { database.ledgerDao().deletePaymentProof(ownerId,proof.id);database.ledgerDao().deleteProofChunks(ownerId,proof.id) }
    }
    runCatching{SyncScheduler.syncNow(context,ownerId)};Unit
}
/** Only materialize a temporary viewing copy when the user explicitly opens a proof. */
fun proofCacheFile(context:Context,proof:PaymentProofEntity):File {
    val dir=File(context.cacheDir,"proofs").apply{mkdirs()}
    dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>24*60*60*1000}?.forEach{it.delete()}
    val hash=MessageDigest.getInstance("SHA-256").digest((proof.ownerId+":"+proof.id).toByteArray()).joinToString(""){"%02x".format(it)}
    val ext=when(proof.mimeType){"application/pdf"->"pdf";"image/png"->"png";"image/webp"->"webp";else->"jpg"}
    return File(dir,"$hash.$ext").apply{writeBytes(proofBytes(proof))}
}
