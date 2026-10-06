package com.lymow.toolkit.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Thrown on HTTP errors; [code] 401 means the session is missing or expired. */
class ApiException(val code: Int, message: String) : Exception(message)

/** Sign-in result for POST /api/access/login. */
sealed interface LoginResult {
    data object Ok : LoginResult
    data class NeedTotp(val step: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}

data class Telemetry(
    val online: Boolean,
    val robotStatus: Int?,
    val battery: Int?,
    val cleanPercent: Int?,
    val errorCodes: List<String>,
    val warningCodes: List<String>,
    val rtkLabel: String?,
    val wifiLabel: String?,
    val asleep: Boolean,
    val thing: String?,
    val pendingTask: Boolean,
) {
    val statusLabel: String
        get() = when (robotStatus) {
            0 -> "Idle"; 1 -> "Waiting"; 2 -> "Mowing"; 3 -> "Paused"
            4 -> "Docking"; 5 -> "Charging"; 6 -> "Remote control"
            7 -> "Error / stopped"; 8 -> "Resuming"; 9 -> "Mapping zone"
            10 -> "Paused (docking)"; 11 -> "Updating"; 12 -> "Charged"
            13 -> "Error / stopped"; 14 -> "Stuck — escaping"; 15 -> "Self-test"
            else -> "Unknown"
        }

    val isMowing: Boolean get() = robotStatus in setOf(2, 8, 9, 14)
    val isPaused: Boolean get() = robotStatus in setOf(3, 10)
    val isDocked: Boolean get() = robotStatus in setOf(0, 1, 4, 5, 12)
    val hasFault: Boolean get() = robotStatus in setOf(7, 13) || errorCodes.isNotEmpty()
}

data class CommandInfo(val name: String, val help: String, val unsafe: Boolean)

data class ScheduleTask(
    val id: String = "",
    val label: String = "",
    val enabled: Boolean = true,
    val freq: String = "weekly", // once | weekly | even | odd
    val date: String? = null,    // YYYY-MM-DD for freq=once
    val days: List<Int> = emptyList(), // 0 = Sunday .. 6 = Saturday
    val timeMin: Int = 8 * 60,
    val zoneNames: List<String> = emptyList(),
    val lastRunDate: String? = null,
    val runCount: Int = 0,
) {
    val timeText: String get() = "%02d:%02d".format(timeMin / 60, timeMin % 60)

    val freqText: String
        get() = when (freq) {
            "once" -> "Once${date?.let { " · $it" } ?: ""}"
            "weekly" -> if (days.isEmpty()) "Weekly" else "Weekly · " + days.sorted()
                .joinToString(" ") { listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[it] }
            "even" -> "Even days"
            "odd" -> "Odd days"
            else -> freq
        }

    fun toJson(): JsonObject = buildJsonObjectSafe {
        if (id.isNotBlank()) put("id", id)
        put("label", label)
        put("enabled", enabled)
        put("freq", freq)
        if (freq == "once" && !date.isNullOrBlank()) put("date", date)
        if (freq == "weekly") put("days", JsonArray(days.map { JsonPrimitive(it) }))
        put("time_min", timeMin)
        if (zoneNames.isNotEmpty()) put("zone_names", JsonArray(zoneNames.map { JsonPrimitive(it) }))
    }
}

data class MowRecord(
    val title: String,
    val subtitle: String,
    val areaM2: Double?,
    val durationMin: Int?,
)

data class MowTotals(
    val totalAreaM2: Double?,
    val totalTimeMin: Int?,
    val mowCount: Int?,
)

data class ZoneFreshness(val name: String, val detail: String, val level: Int) // 0 fresh … 2 stale

data class MapFeature(
    val kind: String,             // zone | robot | dock | rtk_base | no_go | other
    val name: String,
    val polygons: List<List<Pair<Double, Double>>>, // lat/lng rings (outer ring first)
    val point: Pair<Double, Double>? = null,        // lat/lng for point features
)

/**
 * Native client for the Lymow Toolkit server API (reverse-engineered from the
 * v2.10.3 server bundle — see app/server.py there).
 *
 * Auth: the dashboard password is exchanged once at /api/access/login for an
 * HMAC session cookie (`lymow_session`); the app stores only that cookie.
 */
class ToolkitApi(
    private val baseUrl: String,
    private val store: SettingsStore? = null,
    initialCookie: String = "",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cookie: String = initialCookie

    private val root: String get() = baseUrl.trimEnd('/')

    fun baseUrlForDisplay(): String = root

    // ---------- low-level ----------

    private suspend fun call(
        path: String,
        method: String = "GET",
        body: JsonObject? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(root + path)
        if (cookie.isNotBlank()) builder.header("Cookie", "lymow_session=$cookie")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(
                method,
                (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()),
            )
        }
        http.newCall(builder.build()).execute().use { resp ->
            captureCookie(resp.headers("Set-Cookie"))
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401) throw ApiException(401, "auth required")
            if (!resp.isSuccessful) {
                val detail = runCatching {
                    json.parseToJsonElement(text).jsonObject.str("detail")
                }.getOrNull()
                throw ApiException(resp.code, detail ?: "HTTP ${resp.code}")
            }
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw ApiException(resp.code, "Bad response from the Toolkit") }
        }
    }

    private suspend fun captureCookie(headers: List<String>) {
        for (h in headers) {
            val first = h.substringBefore(';')
            if (first.startsWith("lymow_session=")) {
                val value = first.removePrefix("lymow_session=")
                if (value.isNotBlank()) {
                    cookie = value
                    store?.saveSession(value)
                }
            }
        }
    }

    // ---------- auth ----------

    data class AccessStatus(val configured: Boolean, val authed: Boolean)

    /** No session needed: tells whether a dashboard password exists yet. */
    suspend fun accessStatus(): AccessStatus = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url("$root/api/access/status").get()
            if (cookie.isNotBlank()) builder.header("Cookie", "lymow_session=$cookie")
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext AccessStatus(false, false)
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                AccessStatus(obj.bool("configured"), obj.bool("authed"))
            }
        }.getOrThrow()
    }

    /** True when the server answers HTTP at all. */
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(root).get().build()).execute()
                .use { it.code < 500 }
        }.getOrDefault(false)
    }

    suspend fun setupPassword(password: String): LoginResult {
        val resp = runCatching {
            call("/api/access/setup", "POST", buildJsonObjectSafe { put("password", password) })
        }
        return resp.fold(
            onSuccess = { LoginResult.Ok },
            onFailure = {
                LoginResult.Failure((it as? ApiException)?.message ?: "Setup failed")
            },
        )
    }

    suspend fun login(password: String): LoginResult {
        val resp = runCatching {
            call("/api/access/login", "POST", buildJsonObjectSafe { put("password", password) })
        }
        return resp.fold(
            onSuccess = { obj ->
                when {
                    obj.bool("totp") -> LoginResult.NeedTotp(obj.str("step") ?: "")
                    obj.bool("ok") -> LoginResult.Ok
                    else -> LoginResult.Failure(obj.str("detail") ?: "Sign-in failed")
                }
            },
            onFailure = {
                LoginResult.Failure((it as? ApiException)?.message ?: "Sign-in failed")
            },
        )
    }

    suspend fun totp(step: String, code: String): LoginResult {
        val resp = runCatching {
            call(
                "/api/access/totp", "POST",
                buildJsonObjectSafe {
                    put("step", step); put("code", code); put("remember", true)
                },
            )
        }
        return resp.fold(
            onSuccess = { LoginResult.Ok },
            onFailure = {
                LoginResult.Failure((it as? ApiException)?.message ?: "Wrong code")
            },
        )
    }

    suspend fun logout() {
        runCatching { call("/api/access/logout", "POST") }
        cookie = ""
        store?.clearSession()
    }

    // ---------- status & control ----------

    suspend fun telemetry(): Telemetry {
        val obj = call("/api/telemetry")
        val t = obj.obj("telemetry") ?: JsonObject(emptyMap())
        val health = obj.obj("health") ?: JsonObject(emptyMap())
        val robotInfo = t.obj("robotInfo") ?: JsonObject(emptyMap())
        val robotStatus = robotInfo.int("robotStatus") ?: robotInfo.int("workStatus")
            ?: t.int("robotStatus")
        val lastRxAge = health.dbl("last_rx_age")
        val staleAfter = health.dbl("stale_after") ?: 60.0
        return Telemetry(
            online = lastRxAge == null || lastRxAge <= staleAfter,
            robotStatus = robotStatus,
            battery = t.int("battery"),
            cleanPercent = (t.obj("cleanInfo"))?.int("cleanPercent"),
            errorCodes = t.strList("errorCodes"),
            warningCodes = t.strList("warningCodes"),
            rtkLabel = t.obj("rtkFix")?.str("label"),
            wifiLabel = t.obj("netDetailInfo")?.let { net ->
                net.str("ssid") ?: net.str("type") ?: net.str("label")
            },
            asleep = health.bool("asleep"),
            thing = obj.str("thing"),
            pendingTask = obj.obj("pending_task") != null,
        )
    }

    suspend fun commands(): List<CommandInfo> {
        val obj = call("/api/commands")
        val safe = obj.arr("safe").mapNotNull { it.asObj() }
            .map { CommandInfo(it.str("name") ?: "", it.str("help") ?: "", false) }
        val unsafe = obj.arr("unsafe").mapNotNull { it.asObj() }
            .map { CommandInfo(it.str("name") ?: "", it.str("help") ?: "", true) }
        return safe + unsafe
    }

    suspend fun sendCommand(name: String, confirm: Boolean = false): String {
        val obj = call(
            "/api/command/$name", "POST",
            buildJsonObjectSafe { if (confirm) put("confirm", true) },
        )
        return obj.str("name") ?: name
    }

    suspend fun startMow(zoneIds: List<String> = emptyList()) {
        call(
            "/api/mow", "POST",
            buildJsonObjectSafe {
                if (zoneIds.isNotEmpty()) put("zone_ids", JsonArray(zoneIds.map { JsonPrimitive(it) }))
            },
        )
    }

    // ---------- schedules ----------

    suspend fun schedules(): List<ScheduleTask> {
        val obj = call("/api/schedules")
        return obj.arr("tasks").mapNotNull { it.asObj() }.map { t ->
            ScheduleTask(
                id = t.str("id") ?: "",
                label = t.str("label") ?: "",
                enabled = t.bool("enabled", true),
                freq = t.str("freq") ?: "weekly",
                date = t.str("date"),
                days = t.arr("days").mapNotNull { (it as? JsonPrimitive)?.intOrNull },
                timeMin = t.int("time_min") ?: 480,
                zoneNames = t.arr("zone_names").mapNotNull { (it as? JsonPrimitive)?.content },
                lastRunDate = t.str("last_run_date"),
                runCount = t.int("run_count") ?: 0,
            )
        }
    }

    suspend fun saveSchedule(task: ScheduleTask) {
        call("/api/schedule", "POST", task.toJson())
    }

    suspend fun deleteSchedule(id: String) {
        call("/api/schedule/$id", "DELETE")
    }

    suspend fun skipSchedule(id: String) {
        call("/api/schedule/$id/skip", "POST")
    }

    suspend fun runScheduleNow(id: String) {
        call("/api/schedule/$id/run", "POST")
    }

    // ---------- history ----------

    suspend fun mowTotals(): MowTotals {
        val obj = call("/api/mow-totals")
        val t = obj.obj("totals") ?: JsonObject(emptyMap())
        return MowTotals(
            totalAreaM2 = t.dblAny("total_clean_area", "total_area", "area_m2", "total_area_m2"),
            totalTimeMin = t.intAny("total_clean_time", "total_min", "minutes", "total_time_min"),
            mowCount = t.intAny("total_mows", "mows", "count", "total_count", "sessions"),
        )
    }

    suspend fun mowHistory(page: Int = 1, size: Int = 30): List<MowRecord> {
        val obj = call("/api/mow-history?page=$page&size=$size")
        return obj.arr("records").mapNotNull { it.asObj() }.map { r ->
            val ts = r.longAny("ts", "start", "start_ts", "end_ts", "time")
            val dur = r.intAny("duration_min", "duration", "minutes", "clean_time", "time_min")
            val area = r.dblAny("area", "area_m2", "clean_area", "cleaned_area")
            val zones = r.strList("zones") + r.strList("zone_names")
            val status = r.str("status") ?: r.str("result") ?: ""
            MowRecord(
                title = ts?.let { java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                ).format(java.util.Date(if (it > 1e12) it else it * 1000)) }
                    ?: (r.str("date") ?: "Mow"),
                subtitle = listOfNotNull(
                    status.ifBlank { null },
                    zones.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" · "),
                areaM2 = area,
                durationMin = dur,
            )
        }
    }

    // ---------- zones & map ----------

    suspend fun zoneFreshness(): List<ZoneFreshness> {
        val obj = call("/api/zone-staleness")
        return obj.arr("zones").mapNotNull { it.asObj() }.map { z ->
            val name = z.strAny("name", "zone", "zone_name", "label") ?: "Zone"
            val days = z.dblAny("days_since_mow", "days_since", "days", "age_days")
            val level = z.intAny("level", "stale_level", "status_code") ?: when {
                days == null -> 1
                days <= 3.0 -> 0
                days <= 7.0 -> 1
                else -> 2
            }
            ZoneFreshness(
                name = name,
                detail = z.strAny("detail", "text", "label")
                    ?: days?.let { "%.0f days since mow".format(it) } ?: "",
                level = level.coerceIn(0, 2),
            )
        }
    }

    suspend fun mapFeatures(): List<MapFeature> {
        val obj = call("/api/geojson")
        return obj.arr("features").mapNotNull { it.asObj() }.mapNotNull { f ->
            val props = f.obj("properties") ?: JsonObject(emptyMap())
            val kind = props.strAny("type", "kind") ?: "other"
            val name = props.strAny("name", "label") ?: kind
            val geom = f.obj("geometry") ?: return@mapNotNull null
            val gType = geom.str("type") ?: return@mapNotNull null
            val coords = geom["coordinates"] as? JsonArray
            when (gType) {
                "Point" -> {
                    val pt = coords?.toLngLat()
                    MapFeature(kind, name, emptyList(), pt?.let { (lng, lat) -> lat to lng })
                }
                "Polygon" -> {
                    val rings = coords?.mapNotNull { ring ->
                        (ring as? JsonArray)?.mapNotNull { it.toLngLat()?.let { (lng, lat) -> lat to lng } }
                    }.orEmpty()
                    MapFeature(kind, name, rings)
                }
                "MultiPolygon" -> {
                    val rings = coords?.flatMap { poly ->
                        (poly as? JsonArray)?.mapNotNull { ring ->
                            (ring as? JsonArray)?.mapNotNull {
                                it.toLngLat()?.let { (lng, lat) -> lat to lng }
                            }
                        }.orEmpty()
                    }.orEmpty()
                    MapFeature(kind, name, rings)
                }
                else -> null
            }
        }
    }

    // ---------- lenient JSON helpers ----------

    companion object {
        fun normalize(host: String, port: String): String {
            var h = host.trim().trimEnd('/')
            if (h.isEmpty()) return ""
            if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
            val afterScheme = h.substringAfter("://")
            val hasPort = Regex(":\\d+$").containsMatchIn(afterScheme.substringBefore("/"))
            val p = port.trim()
            return if (p.isNotEmpty() && !hasPort) "$h:$p" else h
        }

        private fun JsonElement.asObj(): JsonObject? = this as? JsonObject

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.strAny(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { str(it) }

        private fun JsonObject.int(key: String): Int? =
            (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }

        private fun JsonObject.intAny(vararg keys: String): Int? =
            keys.firstNotNullOfOrNull { int(it) }

        private fun JsonObject.longAny(vararg keys: String): Long? =
            keys.firstNotNullOfOrNull { k ->
                (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
            }

        private fun JsonObject.dbl(key: String): Double? =
            (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

        private fun JsonObject.dblAny(vararg keys: String): Double? =
            keys.firstNotNullOfOrNull { dbl(it) }

        private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
            (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

        private fun JsonObject.arr(key: String): List<JsonElement> =
            (this[key] as? JsonArray)?.toList() ?: emptyList()

        private fun JsonObject.strList(key: String): List<String> =
            arr(key).mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.content.takeIf { el.isString }
                    is JsonObject -> el.strAny("name", "label", "code")
                    else -> null
                }
            }

        private fun JsonElement.toLngLat(): Pair<Double, Double>? {
            val arr = this as? JsonArray ?: return null
            val lng = (arr.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
            val lat = (arr.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
            return lng to lat
        }
    }
}

/** Tiny JSON object builder (avoids experimental buildJsonObject opt-in churn). */
fun buildJsonObjectSafe(build: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
    val map = linkedMapOf<String, JsonElement>()
    map.build()
    return JsonObject(map)
}

private fun MutableMap<String, JsonElement>.put(key: String, value: String) {
    put(key, JsonPrimitive(value))
}

private fun MutableMap<String, JsonElement>.put(key: String, value: Int) {
    put(key, JsonPrimitive(value))
}

private fun MutableMap<String, JsonElement>.put(key: String, value: Boolean) {
    put(key, JsonPrimitive(value))
}
