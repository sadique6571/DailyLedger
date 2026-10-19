package com.sadique.dailyledger.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID

class FinanceRepository(private val repo: LedgerRepository) {
    private val db get() = repo.database
    private val dao get() = db.ledgerDao()
    private val owner get() = repo.ownerId
    suspend fun saveItem(item: FinanceItem) = db.withTransaction {
        val workspace=repo.profileSnapshot().workspace
        require(item.kind !in listOf(FinanceKind.TRANSFER,FinanceKind.ADJUSTMENT) || item.note.isNotBlank()) { "Add a transfer or adjustment reason." }
        if(item.kind==FinanceKind.ACCOUNT) require(workspace.items.none { it.kind==FinanceKind.ACCOUNT && it.title==item.title && it.id!=item.id }) { "Account already exists." }
        val old=workspace.items.find { it.id==item.id }
        if(old?.kind==FinanceKind.ACCOUNT && old.title!=item.title) require(dao.transactionsNow(owner).none { it.account==old.title } && workspace.items.none { it.id!=old.id && (it.account==old.title || it.reference==old.title) }) { "Account is in use; keep its name." }
        require(old==null || old.transactionId.isBlank()) { "A paid record cannot be rewritten. Correct its ledger entry first." }
        repo.updateProfile { it.copy(workspace=it.workspace.put(item)) }
    }
    suspend fun deleteItem(id:String) = db.withTransaction {
        val w=repo.profileSnapshot().workspace; val item=requireNotNull(w.items.find { it.id==id })
        require(item.transactionId.isBlank() && item.settled==0L) { "Keep paid items for their record. Delete or correct their ledger entry first." }
        if(item.kind==FinanceKind.ACCOUNT) require(dao.transactionsNow(owner).none { it.account==item.title } && w.items.none { it.id!=id && (it.account==item.title || it.reference==item.title) }) { "This account is in use." }
        repo.updateProfile { it.copy(workspace=w.copy(items=w.items.filterNot { row -> row.id==id })) }
    }
    suspend fun saveEntry(type:String, amount:Long, category:String, note:String, date:String, account:String="Cash",
        quantity:String="", unit:String="", rate:Long=0, privateEntry:Boolean=false, existing:TransactionEntity?=null):String = db.withTransaction {
        val now=System.currentTimeMillis()
        val row=TransactionEntity(existing?.id ?: "$owner:${UUID.randomUUID()}",owner,type,amount,category,note,date,existing?.createdAt ?: now,now,account,quantity,unit,rate,existing?.refundOf.orEmpty(),privateEntry)
        repo.saveTransactionRow(row, existing?.updatedAt); row.id
    }
    suspend fun refund(parentId:String, amount:Long, date:String, note:String, account:String):String = db.withTransaction {
        val parent=requireNotNull(dao.transactionsNow(owner).find { it.id==parentId }) { "Purchase no longer exists." }
        val now=System.currentTimeMillis(); val id="$owner:${UUID.randomUUID()}"
        repo.saveTransactionRow(TransactionEntity(id,owner,"EXPENSE",amount,parent.category,note,date,now,now,account,refundOf=parent.id,privateEntry=parent.privateEntry));id
    }
    suspend fun payItems(ids:Set<String>, date:String):Unit = db.withTransaction {
        LocalDate.parse(date);val w=repo.profileSnapshot().workspace
        val selected=w.items.filter { it.id in ids }
        require(ids.isNotEmpty() && ids.size<=100 && selected.size==ids.size)
        require(selected.all { it.kind in listOf(FinanceKind.SHOPPING,FinanceKind.PLANNED,FinanceKind.INSTALLMENT) && !it.done && it.transactionId.isBlank() }) { "An item was already paid or changed. Refresh before paying." }
        var next=w
        selected.forEach { item ->
            val txId=saveEntry("EXPENSE",item.amount,item.category,item.title,date,item.account,item.quantity,item.unit,item.rate)
            next=next.put(item.copy(done=true,transactionId=txId,settled=item.amount))
        }
        val actual=dao.transactionsNow(owner)
        repo.updateProfile { it.copy(workspace=it.workspace.copy(items=next.items).reconcileInstallments(actual)) }
    }
    suspend fun splitBill(rows:List<TransactionDraft>, total:Long, account:String, batch:String) = db.withTransaction {
        require(rows.size in 2..50 && rows.all { it.type=="EXPENSE" })
        require(rows.fold(0L) { sum,r -> Math.addExact(sum,r.amountMinor) }==total) { "Split amounts must equal the bill total." }
        UUID.fromString(batch)
        val existing=dao.transactionsNow(owner)
        require(existing.none { it.id.startsWith("$owner:split-$batch-") }) { "This bill is already saved." }
        rows.forEachIndexed { i,d ->
            d.validated();val now=System.currentTimeMillis()
            repo.saveTransactionRow(TransactionEntity("$owner:split-$batch-$i",owner,d.type,d.amountMinor,d.category,d.note,d.date,now,now,account))
        }
    }
    suspend fun createReimbursement(title:String, amount:Long, category:String, date:String, account:String) = db.withTransaction {
        val id=saveEntry("EXPENSE",amount,category,title,date,account)
        saveItem(FinanceItem(kind=FinanceKind.REIMBURSEMENT,title=title,amount=amount,date=date,category=category,account=account,reference=id))
    }
    suspend fun settleReimbursement(id:String, amount:Long, date:String, account:String) = db.withTransaction {
        val w=repo.profileSnapshot().workspace;val item=requireNotNull(w.items.find { it.id==id && it.kind==FinanceKind.REIMBURSEMENT })
        require(amount in 1..item.amount-item.settled) { "Received amount exceeds the claim balance." }
        val txId=saveEntry("INCOME",amount,"Reimbursement",item.title,date,account)
        val receipt=FinanceItem(kind=FinanceKind.CLAIM_RECEIPT,title=item.title,amount=amount,date=date,account=account,reference=item.id,transactionId=txId,done=true)
        repo.updateProfile { current -> current.copy(workspace=current.workspace.put(receipt).put(item.copy(settled=item.settled+amount,done=item.settled+amount==item.amount))) }
    }
    suspend fun createInstallments(title:String,total:Long,advance:Long,count:Int,first:LocalDate,account:String,category:String) = db.withTransaction {
        require(total>0 && advance in 0 until total && count in 1..120)
        val remaining=total-advance;require(remaining>=count)
        val group=UUID.randomUUID().toString()
        val advanceId=if(advance>0)saveEntry("EXPENSE",advance,category,"$title • Advance",LocalDate.now().toString(),account) else ""
        saveItem(FinanceItem(id=group,kind=FinanceKind.INSTALLMENT_PURCHASE,title=title,amount=total,date=first.toString(),category=category,account=account,opening=advance,settled=advance,transactionId=advanceId))
        repeat(count) { n ->
            val amount=remaining/count + if(n.toLong()<remaining%count) 1 else 0
            saveItem(FinanceItem(kind=FinanceKind.INSTALLMENT,title="$title • ${n+1}/$count",amount=amount,date=first.plusMonths(n.toLong()).toString(),category=category,account=account,reference=group))
        }
    }
    suspend fun payAdvance(id:String,date:String,account:String) = db.withTransaction {
        val item=requireNotNull(repo.profileSnapshot().workspace.items.find{it.id==id && it.kind==FinanceKind.INSTALLMENT_PURCHASE})
        require(item.transactionId.isBlank() && item.opening>0)
        val txId=saveEntry("EXPENSE",item.opening,item.category,"${item.title} • Advance",date,account)
        val actual=dao.transactionsNow(owner)
        repo.updateProfile { it.copy(workspace=it.workspace.put(item.copy(transactionId=txId)).reconcileInstallments(actual)) }
    }
    suspend fun cancelInstallments(id:String) = db.withTransaction {
        val w=repo.profileSnapshot().workspace;val item=requireNotNull(w.items.find{it.id==id && it.kind==FinanceKind.INSTALLMENT_PURCHASE})
        require(item.settled==0L && item.transactionId.isBlank() && w.items.none{it.kind==FinanceKind.INSTALLMENT && it.reference==id && it.transactionId.isNotBlank()})
        repo.updateProfile { it.copy(workspace=w.copy(items=w.items.filterNot{row->row.id==id || row.kind==FinanceKind.INSTALLMENT && row.reference==id})) }
    }
    suspend fun contributeGoal(id:String,amount:Long,date:String,account:String="Cash") = db.withTransaction {
        val item=requireNotNull(repo.profileSnapshot().workspace.items.find { it.id==id && it.kind==FinanceKind.GOAL })
        require(amount in 1..item.amount-item.settled)
        LocalDate.parse(date);val savingId="$owner:${UUID.randomUUID()}"
        dao.upsertSaving(SavingEntity(savingId,owner,"DIRECT",amount,date,"Goal • ${item.title}",System.currentTimeMillis()))
        val contribution=FinanceItem(kind=FinanceKind.GOAL_CONTRIBUTION,title=item.title,amount=amount,date=date,account=account,reference=item.id,transactionId=savingId,done=true)
        repo.updateProfile { it.copy(workspace=it.workspace.put(contribution).put(item.copy(settled=item.settled+amount,done=item.settled+amount==item.amount))) }
    }
    suspend fun saveReviewed(rows:List<TransactionDraft>,account:String,batch:String) = db.withTransaction {
        require(rows.size in 1..50);UUID.fromString(batch)
        val existing=dao.transactionsNow(owner)
        val expected=rows.mapIndexed { n,d ->
            d.validated();val now=System.currentTimeMillis()
            TransactionEntity("$owner:review-$batch-$n",owner,d.type,d.amountMinor,d.category,d.note,d.date,now,now,account)
        }
        val saved=existing.filter { it.id.startsWith("$owner:review-$batch-") }
        if(saved.isNotEmpty()) {
            require(saved.size==expected.size && expected.all { r -> saved.any { it.id==r.id && it.type==r.type && it.amountMinor==r.amountMinor && it.category==r.category && it.note==r.note && it.date==r.date && it.account==r.account } }) { "This review was already saved with different details. Start a new review." }
        } else expected.forEach { repo.saveTransactionRow(it) }
    }
    suspend fun check(key:String, checked:Boolean) { require(key.length in 1..200);repo.updateProfile { it.copy(workspace=it.workspace.copy(checked=if(checked) it.workspace.checked+key else it.workspace.checked-key)) } }
}
