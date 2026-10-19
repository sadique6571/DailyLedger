package com.sadique.dailyledger.data

import com.sadique.dailyledger.export.StatementData
import org.junit.Assert.*
import org.junit.Test

class FinancePrivacyExportTest {
    @Test fun privateRowsAreExcludedAndRefundIsShownAsMoneyIn(){
        val public=TransactionEntity("p","o","EXPENSE",10000,"Milk","Public","2026-10-19",1,1)
        val hidden=public.copy(id="secret",note="Private note",privateEntry=true)
        val refund=public.copy(id="refund",amountMinor=2000,refundOf="p",note="Returned")
        val sections=StatementData.sections(listOf(public,hidden,refund),emptyList(),emptyList(),emptyList(),null,null)
        val all=sections.flatMap{it.rows};assertEquals(2,all.size);assertFalse(all.any{it.title.contains("Private")})
        assertTrue(all.any{it.moneyIn && it.amount==2000L})
    }
    @Test fun convertingAnEntryToPrivateProtectsAllOfItsEarlierRevisions(){
        val row=TransactionEntity("p","o","EXPENSE",10000,"Milk","Was public","2026-10-19",1,1)
        val created=EntryRevision(java.util.UUID.randomUUID().toString(),row.id,1,"CREATE","",transactionSnapshot(row))
        val hidden=row.copy(privateEntry=true)
        val changed=EntryRevision(java.util.UUID.randomUUID().toString(),row.id,2,"EDIT",transactionSnapshot(row),transactionSnapshot(hidden))
        assertTrue(row.id in privateRevisionIds(listOf(hidden),listOf(created,changed)))
        assertTrue(row.id in privateRevisionIds(emptyList(),listOf(created,changed)))
    }

    @Test fun olderPrivatePhrasesNeverReappearInPublicLearningAfterRename(){
        val old=TransactionEntity("p","o","EXPENSE",10000,"Restaurant","Secret coffee","2026-10-19",1,1)
        val hidden=old.copy(note="Changed note",privateEntry=true)
        val public=old.copy(id="public",note="Public lunch")
        val revision=EntryRevision(java.util.UUID.randomUUID().toString(),old.id,2,"EDIT",transactionSnapshot(old),transactionSnapshot(hidden))
        val rules=listOf(com.sadique.dailyledger.ai.LearnedCategoryRule("EXPENSE","Secret coffee","Restaurant"),com.sadique.dailyledger.ai.LearnedCategoryRule("EXPENSE","Public lunch","Restaurant"))
        val visible=com.sadique.dailyledger.ai.publicLearningRules(rules,listOf(hidden,public),listOf(revision))
        assertEquals(listOf(rules.last()),visible)
    }

}
