package com.sadique.dailyledger.ai
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
class AiPreferences(context:Context,owner:String) {
    private val cache = com.sadique.dailyledger.security.SecureSecretStore(context, "gemini-cache:"+owner)
    init { val old = context.getSharedPreferences("ai_preferences",Context.MODE_PRIVATE); val edit=old.edit(); old.all.keys.filter{it.endsWith(":tips")||it.endsWith(":hash")}.forEach{edit.remove(it)}; edit.apply() }

    private val prefs=context.getSharedPreferences("ai_preferences",Context.MODE_PRIVATE);private val prefix="gemini-v1:"+digest(owner)+":"
    var enabled:Boolean
        get()=prefs.getBoolean(prefix+"enabled",false)
        set(value){prefs.edit().putBoolean(prefix+"enabled",value).apply();if(!value){cache.clearBytes();prefs.edit().remove(prefix+"attempt").apply()}}
    var attemptedAt:Long
        get()=prefs.getLong(prefix+"attempt",0)
        set(value){prefs.edit().putLong(prefix+"attempt",value).apply()}
    /** Optional proxy base URL (no trailing slash). When set, requests go to the proxy instead of Google directly and no key header is sent. */
    var proxyBase:String
        get()=prefs.getString(prefix+"proxy","").orEmpty()
        set(value){prefs.edit().putString(prefix+"proxy",value.trim()).apply()}
    fun cached(hash:String):List<SpendingTip>?=runCatching {
        val bytes=cache.loadBytes()?:return null
        try { val record=JSONObject(String(bytes,Charsets.UTF_8)); if(record.getString("hash")!=hash)null else AiProtocol.decodeInsights(record.getString("response")) } finally { bytes.fill(0) }
    }.getOrNull()
    fun save(hash:String,tips:List<SpendingTip>){
        val rows=JSONArray().apply{tips.forEach{put(JSONObject().put("title",it.title).put("detail",it.detail))}}
        val bytes=JSONObject().put("hash",hash).put("response",JSONObject().put("suggestions",rows).toString()).toString().toByteArray()
        try { cache.saveBytes(bytes) } finally { bytes.fill(0) }
    }
    internal fun reserve(task:AiTask,now:Long=System.currentTimeMillis()):Boolean=synchronized(limitLock){
        val day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        val sameDay=prefs.getString(prefix+"quota_day",null)==day
        val key=prefix+"quota_"+task.name
        val used=if(sameDay)prefs.getInt(key,0)else 0
        if(used >= if(task==AiTask.INSIGHTS)2 else 10)return@synchronized false
        val edit=prefs.edit()
        if(!sameDay)edit.putString(prefix+"quota_day",day).remove(prefix+"quota_AUTOFILL").remove(prefix+"quota_INSIGHTS")
        edit.putInt(key,used+1).commit()
    }
    companion object{private val limitLock=Any();fun digest(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}}
}
