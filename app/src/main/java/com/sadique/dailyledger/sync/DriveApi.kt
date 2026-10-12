package com.sadique.dailyledger.sync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class DriveApi(private val token:String) {
    private val name="daily-ledger-backup.dlb"
    private fun conn(url:String,method:String="GET",type:String?=null)= (URL(url).openConnection() as HttpURLConnection).apply { requestMethod=method; connectTimeout=20_000; readTimeout=30_000; setRequestProperty("Authorization","Bearer $token"); type?.let{setRequestProperty("Content-Type",it)} }
    private fun body(c:HttpURLConnection):ByteArray { val code=c.responseCode; val bytes=(if(code in 200..299)c.inputStream else c.errorStream)?.readBytes()?:byteArrayOf(); if(code !in 200..299) throw IOException("Drive HTTP $code: ${String(bytes)}"); return bytes }
    fun findId():String? { val q=URLEncoder.encode("name = '$name' and trashed = false","UTF-8"); val c=conn("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=100"); val json=JSONObject(String(body(c))); val a=json.getJSONArray("files"); return if(a.length()>0)a.getJSONObject(0).getString("id") else null }
    fun upload(data:ByteArray) {
        val id=findId() ?: create()
        // HttpURLConnection rejects the PATCH verb, so send POST with Google's method-override header.
        val c=conn("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media","POST","application/octet-stream")
        c.setRequestProperty("X-HTTP-Method-Override","PATCH")
        c.doOutput=true
        c.outputStream.use{it.write(data)}
        body(c)
    }
    fun download():ByteArray? { val id=findId()?:return null; return body(conn("https://www.googleapis.com/drive/v3/files/$id?alt=media")) }
    private fun create():String { val c=conn("https://www.googleapis.com/drive/v3/files?fields=id","POST","application/json; charset=UTF-8"); c.doOutput=true; c.outputStream.use{it.write(JSONObject().put("name",name).put("parents",org.json.JSONArray().put("appDataFolder")).toString().toByteArray())}; return JSONObject(String(body(c))).getString("id") }
}
