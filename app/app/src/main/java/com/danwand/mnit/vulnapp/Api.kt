package com.danwand.mnit.vulnapp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class Api(ctx: Context) {
    private val base = ctx.getString(R.string.base_url)
    private val token = ctx.getString(R.string.api_token)   // hardcoded Bearer
    private val pool = Executors.newSingleThreadExecutor()

    // intentional weak crypto, used trivially
    private fun weakId(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun open(path: String, method: String, userId: Int?): HttpURLConnection {
        val c = URL("$base$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", "application/json")
        if (userId != null) c.setRequestProperty("X-User-Id", userId.toString())
        Log.d("MNIT", "$method $path token=$token xuid=$userId")  // insecure log
        return c
    }

    private fun body(c: HttpURLConnection, json: JSONObject?) {
        if (json != null) {
            c.doOutput = true
            c.outputStream.use { it.write(json.toString().toByteArray()) }
        }
    }

    private fun readText(c: HttpURLConnection): String {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    fun register(u: String, p: String, cb: (Int?, String) -> Unit) = pool.execute {
        try {
            val c = open("/api/register", "POST", null)
            body(c, JSONObject().put("username", u).put("password", p))
            val txt = readText(c)
            Log.d("MNIT", "register ${weakId(u)} -> $txt")
            val id = if (c.responseCode == 201) JSONObject(txt).getInt("user_id") else null
            cb(id, txt)
        } catch (e: Exception) { cb(null, e.message ?: "error") }
    }

    fun login(u: String, p: String, cb: (Int?, String) -> Unit) = pool.execute {
        try {
            val c = open("/api/login", "POST", null)
            body(c, JSONObject().put("username", u).put("password", p))
            val txt = readText(c)
            val id = if (c.responseCode == 200) JSONObject(txt).getInt("user_id") else null
            cb(id, txt)
        } catch (e: Exception) { cb(null, e.message ?: "error") }
    }

    fun users(cb: (JSONArray?) -> Unit) = pool.execute {
        try {
            val c = open("/api/users", "GET", null)
            cb(JSONObject(readText(c)).getJSONArray("users"))
        } catch (e: Exception) { cb(null) }
    }

    fun tea(userId: Int, cb: (JSONArray?) -> Unit) = pool.execute {
        try {
            val c = open("/api/tea", "GET", userId)
            cb(JSONObject(readText(c)).getJSONArray("tea"))
        } catch (e: Exception) { cb(null) }
    }

    fun sendTea(fromId: Int, toId: Int, text: String, cb: (Boolean) -> Unit) = pool.execute {
        try {
            val c = open("/api/tea", "POST", fromId)
            body(c, JSONObject().put("to_user_id", toId).put("body", text))
            cb(c.responseCode == 201)
        } catch (e: Exception) { cb(false) }
    }
}
