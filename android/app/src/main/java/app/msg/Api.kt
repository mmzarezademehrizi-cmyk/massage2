package app.msg

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// آدرس Worker بعد از دیپلوی روی کلودفلر
const val BASE = "https://REPLACE.workers.dev"

data class Msg(val id: Long, val from: String, val to: String, val text: String?, val key: String?, val name: String?, val type: String?)

private fun JSONObject.s(k: String): String? = if (isNull(k)) null else getString(k)

private fun parse(s: String): List<Msg> {
    val a = JSONArray(s)
    return List(a.length()) {
        val o = a.getJSONObject(it)
        Msg(o.getLong("id"), o.getString("sender"), o.getString("receiver"), o.s("text"), o.s("file_key"), o.s("file_name"), o.s("file_type"))
    }
}

class Api(ctx: Context) {
    private val sp = ctx.getSharedPreferences("s", 0)
    var token: String?
        get() = sp.getString("t", null)
        set(v) { sp.edit().putString("t", v).apply() }
    var me: String?
        get() = sp.getString("u", null)
        set(v) { sp.edit().putString("u", v).apply() }

    private fun call(method: String, path: String, body: String? = null): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val ok = c.responseCode in 200..299
        val s = (if (ok) c.inputStream else c.errorStream).bufferedReader().readText()
        if (!ok) throw Exception(s)
        return s
    }

    fun auth(register: Boolean, u: String, p: String) {
        val r = JSONObject(call("POST", if (register) "/register" else "/login", JSONObject().put("username", u).put("password", p).toString()))
        token = r.getString("token")
        me = r.getString("username")
    }

    fun chats() = parse(call("GET", "/chats"))
    fun messages(with: String, after: Long) = parse(call("GET", "/messages?with=$with&after=$after"))

    fun send(to: String, text: String?, key: String? = null, name: String? = null, type: String? = null) {
        call("POST", "/send", JSONObject().put("to", to).put("text", text).put("file_key", key).put("file_name", name).put("file_type", type).toString())
    }

    fun upload(ctx: Context, uri: Uri): Triple<String, String, String> {
        val type = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        } ?: "file"
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val c = URL("$BASE/upload").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"
        c.doOutput = true
        c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Content-Type", type)
        c.outputStream.use { it.write(bytes) }
        val key = JSONObject(c.inputStream.bufferedReader().readText()).getString("key")
        return Triple(key, name, type)
    }

    fun fileBytes(key: String): ByteArray {
        val c = URL("$BASE/file/$key").openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        return c.inputStream.use { it.readBytes() }
    }
}

fun saveDownload(ctx: Context, name: String, type: String, bytes: ByteArray) {
    val v = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, type)
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
}
