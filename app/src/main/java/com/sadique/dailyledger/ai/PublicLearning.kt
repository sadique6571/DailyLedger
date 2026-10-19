package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.EntryRevision
import com.sadique.dailyledger.data.TransactionEntity
import com.sadique.dailyledger.data.privateRevisionIds
import org.json.JSONObject

/** A later private edit must also protect phrases learned from an earlier public version of that entry. */
fun publicLearningRules(rules:List<LearnedCategoryRule>,rows:List<TransactionEntity>,revisions:List<EntryRevision>):List<LearnedCategoryRule> {
    val hiddenIds=privateRevisionIds(rows,revisions)
    if(hiddenIds.isEmpty())return rules
    val snapshots=revisions.filter{it.transactionId in hiddenIds}.flatMap{listOf(it.before,it.after)}.filter{it.isNotBlank()}.map{JSONObject(it)}
    val notes=(rows.filter{it.id in hiddenIds}.map{it.note}+snapshots.map{it.optString("note")}).filter{it.isNotBlank()}.map(CategoryLearning::normalize).distinct()
    val categories=(rows.filter{it.id in hiddenIds}.map{it.category}+snapshots.map{it.optString("category")}).toSet()
    val publicCategories=rows.filterNot{it.privateEntry}.map{it.category}.toSet()
    return rules.filterNot{rule->val phrase=CategoryLearning.normalize(rule.phrase);notes.any{phrase in it} || rule.category in categories && rule.category !in publicCategories}
}
