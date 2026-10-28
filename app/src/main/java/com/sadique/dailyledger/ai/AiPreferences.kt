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
    /** Which AI service the personal key belongs to (see AiProviders). Default: Gemini. */
    var providerId:String
        get()=prefs.getString(prefix+"provider","gemini").orEmpty().ifBlank{"gemini"}
        set(value){prefs.edit().putString(prefix+"provider",value.trim()).apply()}
    /** Per-provider model override; blank = provider default. */
    fun modelFor(providerId: String): String =
        prefs.getString(prefix + "pmodel:" + providerId, "").orEmpty()
    fun setModel(providerId: String, value: String) {
        prefs.edit().putString(prefix + "pmodel:" + providerId, value.trim()).apply()
    }
    /** Per-provider base URL override (only the "custom" provider uses one). */
    fun baseFor(providerId: String): String =
        prefs.getString(prefix + "pbase:" + providerId, "").orEmpty()
    fun setBase(providerId: String, value: String) {
        prefs.edit().putString(prefix + "pbase:" + providerId, value.trim()).apply()
    }
    /** Current provider's model/base, for AiService.route(). */
    var providerModel:String
        get()=modelFor(providerId)
        set(value){setModel(providerId,value)}
    var providerBase:String
        get()=baseFor(providerId)
        set(value){setBase(providerId,value)}
    /** One-time move of the old single model/base slots into the currently
     * selected provider's slots. Idempotent: no-ops once the old slots are empty. */
    fun migrateLegacy() {
        val legacyModel = prefs.getString(prefix + "pmodel", "").orEmpty()
        if (legacyModel.isNotBlank() && modelFor(providerId).isBlank()) {
            setModel(providerId, legacyModel)
        }
        val legacyBase = prefs.getString(prefix + "pbase", "").orEmpty()
        if (legacyBase.isNotBlank() && baseFor(providerId).isBlank()) {
            setBase(providerId, legacyBase)
        }
        if (legacyModel.isNotBlank() || legacyBase.isNotBlank()) {
            prefs.edit().remove(prefix + "pmodel").remove(prefix + "pbase").apply()
        }
    }
    /** AI entry engine choice (Online vs Offline). Null = never chosen; the
     * ViewModel then defaults to Online when Cloud AI is enabled, else Offline. */
    var entryEngine:AiEntryEngine?
        get()=prefs.getString(prefix+"engine",null)?.let{runCatching{AiEntryEngine.valueOf(it)}.getOrNull()}
        set(value){prefs.edit().apply{if(value==null)remove(prefix+"engine")else putString(prefix+"engine",value.name)}.apply()}
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

/** Which engine produces an AI entry: ONLINE (Cloudflare proxy default, or the
 * user's personal key when set) or OFFLINE (on-device catalog/vocabulary path). */
enum class AiEntryEngine { ONLINE, OFFLINE }
