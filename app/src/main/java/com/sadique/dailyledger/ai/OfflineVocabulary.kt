package com.sadique.dailyledger.ai

import java.util.Locale

/** Conservative spelling aliases, shared by search, category rules and local learning.
 * Deliberately avoids fuzzy guesses about people, amounts or dates.
 */
object OfflineVocabulary {
    private val aliases = mapOf(
        "dudh" to "doodh", "dodh" to "doodh", "dood" to "doodh", "دودھ" to "doodh", "milk" to "doodh",
        "sabji" to "sabzi", "sabzee" to "sabzi", "sabziyan" to "sabzi", "سبزی" to "sabzi",
        "aalo" to "aalu", "aaloo" to "aalu", "aloo" to "aalu", "آلو" to "aalu", "potato" to "aalu", "potatoes" to "aalu",
        "piyaz" to "pyaaz", "pyaz" to "pyaaz", "payaz" to "pyaaz", "پیاز" to "pyaaz",
        "tamater" to "tamatar", "tamato" to "tamatar", "ٹماٹر" to "tamatar",
        "lahsan" to "lehsan", "lahsun" to "lehsan", "لہسن" to "lehsan",
        "daal" to "dal", "دال" to "dal", "mung" to "moong", "mash" to "maash",
        "masur" to "masoor", "chanay" to "chana", "channay" to "chana",
        "chaval" to "chawal", "چاول" to "chawal", "aata" to "atta", "آٹا" to "atta",
        "andey" to "anday", "ande" to "anday", "انڈے" to "anday",
        "murgi" to "murghi", "مرغی" to "murghi",
        "kelaa" to "kela", "kelay" to "kela", "کیلا" to "kela",
        "kheera" to "khira", "bhindi" to "bhindi", "dhaniya" to "dhania",
        "tankhwah" to "tankhwa", "tankha" to "tankhwa",
        "khareeda" to "kharida", "khareedi" to "kharidi", "khrcha" to "kharcha",
        "dawai" to "dawa", "dawaye" to "dawa", "dawaiyan" to "dawa",
        "petrole" to "petrol", "paitrol" to "petrol", "پٹرول" to "petrol",
    )

    fun normalize(text: String): String {
        val clean = text.lowercase(Locale.ROOT).replace('ي', 'ی').replace('ك', 'ک')
        return Regex("[\\p{L}]+").replace(clean) { aliases[it.value] ?: it.value }
    }
}
