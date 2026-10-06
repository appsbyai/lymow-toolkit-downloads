package com.lymow.toolkit.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Snapshot of what we could learn from the Toolkit server.
 *
 * The Toolkit's web dashboard is the authoritative UI. Its native JSON surface is
 * undocumented, so the client probes a few conventional endpoints and parses
 * leniently; [nativeApi] is false when only the web UI itself is available.
 */
data class MowerStatus(
    val reachable: Boolean,
    val nativeApi: Boolean,
    val state: String? = null,
    val batteryPercent: Int? = null,
    val rtkFix: String? = null,
    val wifiDbm: Int? = null,
    val nextMow: String? = null,
)

class ToolkitClient(private val baseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val root: String get() = baseUrl.trimEnd('/')

    /** True when the server answers HTTP at all (any response below 500). */
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(root).head().build()).execute()
                .use { it.code < 500 }
        }.recoverCatching {
            http.newCall(Request.Builder().url(root).get().build()).execute()
                .use { it.code < 500 }
        }.getOrDefault(false)
    }

    private suspend fun getBody(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(root + path).get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    /** Best-effort native status: tries conventional JSON endpoints, parses leniently. */
    suspend fun fetchStatus(): MowerStatus {
        if (!probe()) return MowerStatus(reachable = false, nativeApi = false)
        for (path in listOf("/api/status", "/api/state", "/api/mower/status", "/status")) {
            val body = getBody(path) ?: continue
            val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: continue
            return MowerStatus(
                reachable = true,
                nativeApi = true,
                state = obj.firstString("state", "status", "mode", "activity"),
                batteryPercent = obj.firstInt("battery", "battery_percent", "batteryPercent", "soc"),
                rtkFix = obj.firstString("rtk", "rtk_status", "rtkState", "gps", "gps_status"),
                wifiDbm = obj.firstInt("wifi_dbm", "rssi", "wifi", "signal_dbm"),
                nextMow = obj.firstString("next_mow", "nextMow", "next_schedule", "nextRun"),
            )
        }
        return MowerStatus(reachable = true, nativeApi = false)
    }

    /** Fire a mower action. Returns true when any known endpoint accepted it. */
    suspend fun sendAction(action: String): Boolean = withContext(Dispatchers.IO) {
        val payload = """{"action":"$action"}""".toRequestBody("application/json".toMediaType())
        for (path in listOf("/api/mower/action", "/api/action", "/api/control")) {
            val accepted = runCatching {
                http.newCall(Request.Builder().url(root + path).post(payload).build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (accepted) return@withContext true
        }
        false
    }

    private fun JsonObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        }

    private fun JsonObject.firstInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            this[key]?.let { el ->
                runCatching { el.jsonPrimitive.intOrNull ?: el.jsonPrimitive.content.toIntOrNull() }
                    .getOrNull()
            }
        }

    companion object {
        private val PORT_AT_END = Regex(":\\d+$")

        /** Build a base URL from free-form user input like "192.168.1.50" + "8787". */
        fun normalize(host: String, port: String): String {
            var h = host.trim().trimEnd('/')
            if (h.isEmpty()) return ""
            if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
            val afterScheme = h.substringAfter("://")
            val hasPort = PORT_AT_END.containsMatchIn(afterScheme.substringBefore("/"))
            val p = port.trim()
            return if (p.isNotEmpty() && !hasPort) "$h:$p" else h
        }
    }
}
