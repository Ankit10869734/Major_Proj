package com.moodmix.app.data.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatStorage(context: Context) {

    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    @Synchronized
    fun loadAll(): List<ChatSession> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
            .mapNotNull { f ->
                try { fromJson(JSONObject(f.readText())) } catch (_: Exception) { null }
            }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun save(session: ChatSession) {
        try {
            val tmp = File(dir, "${session.id}.json.tmp")
            tmp.writeText(toJson(session).toString())
            tmp.renameTo(File(dir, "${session.id}.json"))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun delete(id: String) {
        try { File(dir, "$id.json").delete() } catch (_: Exception) {}
    }
        /** fileName -> (prompt, durationSeconds) for every generated track in saved chats. */
    @Synchronized
    fun trackIndex(): Map<String, Pair<String, Int>> {
        val map = HashMap<String, Pair<String, Int>>()
        for (chat in loadAll()) {
            for (m in chat.messages) {
                val name = m.audioFileName
                if (!m.isUser && name != null) map[name] = m.text to m.durationSeconds
            }
        }
        return map
    }

    private fun toJson(s: ChatSession): JSONObject {
        val msgs = JSONArray()
        for (m in s.messages) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("isUser", m.isUser)
            o.put("text", m.text)
            o.put("duration", m.durationSeconds)
            o.put("audio", m.audioFileName ?: "")
            o.put("exported", m.exportedUri ?: "")
            o.put("status", m.status.name)
            o.put("error", m.errorText ?: "")
            o.put("ts", m.timestamp)
            msgs.put(o)
        }
        val root = JSONObject()
        root.put("id", s.id)
        root.put("title", s.title)
        root.put("createdAt", s.createdAt)
        root.put("updatedAt", s.updatedAt)
        root.put("messages", msgs)
        return root
    }

    private fun fromJson(o: JSONObject): ChatSession {
        val arr = o.optJSONArray("messages") ?: JSONArray()
        val msgs = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            ChatMessage(
                id = m.getString("id"),
                isUser = m.optBoolean("isUser"),
                text = m.optString("text"),
                durationSeconds = m.optInt("duration"),
                audioFileName = m.optString("audio").takeIf { it.isNotEmpty() },
                exportedUri = m.optString("exported").takeIf { it.isNotEmpty() },
                status = try { MessageStatus.valueOf(m.optString("status", "DONE")) } catch (_: Exception) { MessageStatus.DONE },
                errorText = m.optString("error").takeIf { it.isNotEmpty() },
                timestamp = m.optLong("ts")
            )
        }
        return ChatSession(
            id = o.getString("id"),
            title = o.optString("title", "Chat"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            messages = msgs
        )
    }
}

/** Finds and deletes generated audio files. */
object GeneratedFiles {

    fun find(context: Context, fileName: String): File? {
        val a = File(File(context.filesDir, "generated_music"), fileName)
        if (a.exists()) return a
        val b = File(File(context.cacheDir, "moodmix_generated"), fileName)
        return if (b.exists()) b else null
    }

    /** Deletes the persistent copy AND the cache copies. Your Library's migrate function would otherwise copy a cached file back. */
    fun delete(context: Context, fileName: String) {
        listOf(
            File(File(context.filesDir, "generated_music"), fileName),
            File(File(context.cacheDir, "moodmix_generated"), fileName),
            File(File(context.cacheDir, "generated_audio"), fileName)
        ).forEach { try { it.delete() } catch (_: Exception) {} }
    }
}
/** Turns a long prompt into a short, readable track title. */
fun friendlyTitle(prompt: String): String {
    val clean = prompt.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return "AI track"
    val parts = clean.split(Regex("[,.;]\\s*")).filter { it.isNotBlank() }
    var title = parts.firstOrNull() ?: clean
    if (title.length < 14 && parts.size > 1) title = "${parts[0]}, ${parts[1]}"
    if (title.length > 42) {
        val cut = title.take(42)
        title = cut.substringBeforeLast(' ', cut).trimEnd(',', ' ') + "…"
    }
    return title.replaceFirstChar { it.uppercase() }
}