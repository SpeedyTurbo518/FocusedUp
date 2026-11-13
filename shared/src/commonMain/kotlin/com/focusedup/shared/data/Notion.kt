package com.focusedup.shared.data

import com.focusedup.shared.AppConfig
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.toLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The kinds of jobs the content filter knows about. */
val JOB_TYPES = listOf("Video", "Photo", "Graphic", "Other")

@Serializable
data class NotionConnection(
    val token: String,
    val workspace: String = "",
    val account: String = "",
    val databases: List<NotionDatabase> = emptyList(),
    /** Signed in with the user's own Notion login (read through notion.so, works for guests). */
    val cookie: String? = null,
    /** Notion user id of the signed-in person (to keep only their items). */
    val myId: String = "",
    /** "workspace" once databases come only from the user's own workspace page. */
    val scope: String = "",
    /** Connected with a link from the workspace owner (read-only, shared by them). */
    val shared: Boolean = false,
)

@Serializable
data class NotionDatabase(val id: String, val title: String, val view: String = "", val space: String = "")

/** Something worth a chime: an item was completed, sent to review, or reassigned. */
data class NotionChange(val item: NotionItem, val headline: String, val detail: String)

/** "complete", "review" or null for a Notion status name. */
fun statusKind(status: String?): String? {
    val t = status?.lowercase() ?: return null
    return when {
        "review" in t -> "review"
        listOf("complete", "done", "posted", "published", "approved", "finished", "delivered", "scheduled", "needs payment", "paid")
            .any { it in t } -> "complete"
        else -> null
    }
}

/** One row from a Notion database (a shoot, project, task...). */
@Serializable
data class NotionItem(
    val id: String,
    val title: String,
    val date: LocalDate? = null,
    /** Arrival / call time, already formatted for display. */
    val arrival: String? = null,
    /** Raw job/type value from Notion, e.g. "Video shoot". */
    val typeRaw: String? = null,
    val status: String? = null,
    val location: String? = null,
    val url: String = "",
    val edited: String = "",
    val database: String = "",
    /** People in "Assigned to" / "Editor" / similar columns. */
    val assignees: List<String> = emptyList(),
    /** True once a video file is attached (an upload finished). */
    val hasVideo: Boolean = false,
    /** "recording" (Shoot Log), "task" (Weekly Tasks) or "content" (client calendars). */
    val kind: String = "content",
    /** Every tag from multi-select columns (platforms, topics...). */
    val tags: List<String> = emptyList(),
) {
    /** A video shoot: a Shoot Log entry, or a task like "Content Recording". */
    val isShoot: Boolean
        get() = kind == "recording" || (kind == "task" && listOf("record", "shoot").any { it in title.lowercase() })

    /** Video, Photo, Graphic or Other. */
    val jobType: String
        get() {
            val t = "${typeRaw.orEmpty()} $title".lowercase()
            return when {
                "video" in t || "film" in t || "reel" in t -> "Video"
                "photo" in t || "headshot" in t || "portrait" in t -> "Photo"
                "graphic" in t || "design" in t || "logo" in t || "flyer" in t -> "Graphic"
                else -> "Other"
            }
        }
}

/** What the user wants to be updated about (Calendar → Notion → Filter). */
@Serializable
data class NotionFilter(
    /** 0 = this week, 1 = this month, 2 = a chosen month, 3 = everything upcoming. */
    val range: Int = 1,
    /** For range 2: "2026-12". */
    val month: String? = null,
    val types: Set<String> = JOB_TYPES.toSet(),
    val keyword: String = "",
    val showShootDate: Boolean = true,
    val showArrival: Boolean = true,
    val showLocation: Boolean = true,
    val showStatus: Boolean = false,
    /** Chime + notification when one of my items goes to review. */
    val notifyReview: Boolean = true,
    /** Automatic reminder for every upcoming shoot: -1 off, 0 one hour before, 1 morning of, 2 day before. */
    val shootReminder: Int = -1,
    /** Push notification when a video upload finishes. */
    val notifyUpload: Boolean = true,
    /** Notify when a matching item is added or changed. */
    val notify: Boolean = true,
)

data class NotionUiState(
    val connection: NotionConnection? = null,
    val items: List<NotionItem> = emptyList(),
    val filter: NotionFilter = NotionFilter(),
    val syncing: Boolean = false,
    val lastSync: Long = 0L,
    val error: String? = null,
    /** Databases the account shared with Focused Up (for the picker). */
    val available: List<NotionDatabase> = emptyList(),
)

/**
 * Reads upcoming shoots and projects from the user's Notion databases.
 * Login uses Notion's own sign-in page (Google, Apple or email), or an integration token.
 */
@OptIn(ExperimentalEncodingApi::class)
class NotionStore(private val store: KeyValueStore, private val platform: FocusPlatform) {
    companion object {
        const val OAUTH_PORT = 53682
        const val REDIRECT_URI = "http://localhost:$OAUTH_PORT/notion"
        private const val API = "https://api.notion.com/v1"
        private const val VERSION = "2022-06-28"
        val loginConfigured: Boolean get() = AppConfig.NOTION_CLIENT_ID.isNotBlank() && AppConfig.NOTION_CLIENT_SECRET.isNotBlank()
    }

    private val _state = MutableStateFlow(
        NotionUiState(
            connection = load<NotionConnection>("notion_conn"),
            items = load<List<NotionItem>>("notion_items") ?: emptyList(),
            filter = load<NotionFilter>("notion_filter") ?: NotionFilter(),
            lastSync = store.get("notion_last_sync")?.toLongOrNull() ?: 0L,
        )
    )
    val state: StateFlow<NotionUiState> = _state.asStateFlow()

    private inline fun <reified T> load(key: String): T? =
        store.get(key)?.takeIf { it.isNotEmpty() }?.let { runCatching { AppJson.decodeFromString<T>(it) }.getOrNull() }

    private fun setConnection(c: NotionConnection?) {
        _state.value = _state.value.copy(connection = c)
        store.put("notion_conn", if (c == null) "" else AppJson.encodeToString(c))
    }

    fun setFilter(f: NotionFilter) {
        _state.value = _state.value.copy(filter = f)
        store.put("notion_filter", AppJson.encodeToString(f))
    }

    // ---------------- Sign in ----------------

    /** Opens Notion's sign-in page in the browser (Continue with Google / email) and finishes the login. */
    suspend fun signIn(): String? {
        if (!loginConfigured) return "Notion sign-in isn't set up in this build yet. Use an integration token instead."
        val url = "https://api.notion.com/v1/oauth/authorize?client_id=${AppConfig.NOTION_CLIENT_ID}" +
            "&response_type=code&owner=user&redirect_uri=${encode(REDIRECT_URI)}"
        val params = platform.awaitOAuthRedirect(url, OAUTH_PORT) ?: return "Sign-in was cancelled."
        params["error"]?.let { return "Notion said: $it" }
        val code = params["code"] ?: return "Sign-in didn't finish. Please try again."
        val basic = Base64.encode("${AppConfig.NOTION_CLIENT_ID}:${AppConfig.NOTION_CLIENT_SECRET}".encodeToByteArray())
        val body = buildJsonObject {
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", REDIRECT_URI)
        }.toString()
        val res = platform.http(
            "POST", "$API/oauth/token",
            mapOf("Authorization" to "Basic $basic", "Content-Type" to "application/json", "Notion-Version" to VERSION),
            body,
        )
        if (res.code != 200) return errorText(res.code, res.body)
        val json = runCatching { AppJson.parseToJsonElement(res.body).jsonObject }.getOrNull() ?: return "Unexpected reply from Notion."
        val token = json.str("access_token") ?: return "Notion didn't send an access token."
        val owner = json["owner"]?.obj?.get("user")?.obj
        val account = owner?.get("person")?.obj?.str("email") ?: owner?.str("name") ?: ""
        setConnection(NotionConnection(token, json.str("workspace_name") ?: "Notion", account))
        return loadDatabases()
    }

    /**
     * Connects with a read-only connection the workspace owner set up (sent as a one-time
     * focusedup://notion#t=... link), so the user never has to sign in or handle a key.
     */
    suspend fun connectWithToken(token: String, shared: Boolean = true): String? {
        val t = token.trim()
        if (t.length < 20) return "That connection link looks incomplete. Ask for a new one."
        val res = platform.http("GET", "$API/users/me", headers(t), null)
        if (res.code != 200) return errorText(res.code, res.body)
        val me = runCatching { AppJson.parseToJsonElement(res.body).jsonObject }.getOrNull()
        val name = me?.get("bot")?.obj?.get("workspace_name")?.jsonPrimitive?.contentOrNull ?: me?.str("name") ?: "Notion"
        setConnection(NotionConnection(t, name, "", shared = shared))
        return loadDatabases()
    }

    /** Signed in on Notion's own page inside the app; [cookie] is that browser session. */
    suspend fun signInWithSession(cookie: String): String? {
        val name = try {
            NotionWebClient(platform, cookie).whoAmI()
        } catch (e: NotionWebClient.Expired) {
            return "Notion didn't finish signing in. Please try again."
        } catch (e: Exception) {
            return e.message ?: "Couldn't reach Notion."
        }
        setConnection(NotionConnection(token = "", workspace = "Notion", account = name, cookie = cookie))
        return loadDatabases()
    }

    fun disconnect() {
        setConnection(null)
        _state.value = _state.value.copy(items = emptyList(), available = emptyList(), error = null, lastSync = 0L)
        store.put("notion_items", "")
        store.put("notion_last_sync", "0")
    }

    /** Lists the databases the account shared with Focused Up. Selects them all the first time. */
    suspend fun loadDatabases(): String? {
        val c = _state.value.connection ?: return null
        c.cookie?.let { cookie ->
            val client = NotionWebClient(platform, cookie)
            val (myId, myName) = try {
                client.me()
            } catch (e: NotionWebClient.Expired) {
                return "Your Notion sign-in expired. Sign in again.".also { _state.value = _state.value.copy(error = it) }
            } catch (e: Exception) {
                return (e.message ?: "Couldn't reach Notion.").also { _state.value = _state.value.copy(error = it) }
            }
            // Only the lists inside "<name>'s Workspace"; the rest of the Notion account is ignored.
            val dbs = runCatching { client.workspaceDatabases(myName.ifBlank { c.account }) }.getOrElse {
                return (it.message ?: "Couldn't read your workspace.").also { msg -> _state.value = _state.value.copy(error = msg) }
            }
            _state.value = _state.value.copy(available = dbs, error = null)
            setConnection(c.copy(databases = dbs, myId = myId, account = myName.ifBlank { c.account }, scope = "workspace"))
            return if (dbs.isEmpty()) "Couldn't find \"${myName.substringBefore(' ')}'s Workspace\" in your Notion sidebar." else null
        }
        val body = """{"filter":{"property":"object","value":"database"},"page_size":100}"""
        val res = platform.http("POST", "$API/search", headers(c.token), body)
        if (res.code != 200) return errorText(res.code, res.body).also { _state.value = _state.value.copy(error = it) }
        val results = runCatching { AppJson.parseToJsonElement(res.body).jsonObject["results"]!!.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        val dbs = results.mapNotNull { el ->
            val o = el.obj ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val title = o["title"]?.let(::plainText).orEmpty().ifBlank { "Untitled database" }
            NotionDatabase(id, title)
        }
        _state.value = _state.value.copy(available = dbs, error = null)
        if (c.databases.isEmpty() && dbs.isNotEmpty()) setConnection(c.copy(databases = dbs))
        if (dbs.isEmpty()) return "No databases found. In Notion, open your shoots/projects database → ••• → Connections → add Focused Up."
        return null
    }

    fun setDatabases(dbs: List<NotionDatabase>) {
        val c = _state.value.connection ?: return
        setConnection(c.copy(databases = dbs))
    }

    // ---------------- Sync ----------------

    /**
     * Pulls the latest rows. Returns the items that were completed, sent to review or reassigned
     * since the last sync (empty on the first sync), so the caller can chime.
     */
    suspend fun sync(@Suppress("UNUSED_PARAMETER") today: LocalDate): List<NotionChange> {
        _state.value.connection?.let { c0 ->
            if (c0.databases.isEmpty() || (c0.cookie != null && c0.scope != "workspace")) loadDatabases()
        }
        val c = _state.value.connection ?: return emptyList()
        if (_state.value.syncing) return emptyList()
        _state.value = _state.value.copy(syncing = true, error = null)
        val before = _state.value.items.associateBy { it.id }
        val firstSync = _state.value.lastSync == 0L
        val all = mutableListOf<NotionItem>()
        var error: String? = null
        val web = c.cookie?.let { NotionWebClient(platform, it) }
        for (db in c.databases) {
            if (web != null) {
                try {
                    all += web.items(db, c.myId, c.account)
                } catch (e: NotionWebClient.Expired) {
                    error = "Your Notion sign-in expired. Sign in again."
                    break
                } catch (e: Exception) {
                    error = e.message ?: "Couldn't read ${db.title}."
                }
                continue
            }
            var cursor: String? = null
            var pages = 0
            do {
                val body = buildJsonObject {
                    put("page_size", 100)
                    if (cursor != null) put("start_cursor", cursor)
                }.toString()
                val res = platform.http("POST", "$API/databases/${db.id}/query", headers(c.token), body)
                if (res.code != 200) {
                    error = errorText(res.code, res.body)
                    break
                }
                val json = runCatching { AppJson.parseToJsonElement(res.body).jsonObject }.getOrNull() ?: break
                json["results"]?.jsonArray?.forEach { el -> el.obj?.let { parsePage(it, db.title) }?.let(all::add) }
                cursor = if (json["has_more"]?.jsonPrimitive?.contentOrNull == "true") json.str("next_cursor") else null
                pages++
            } while (cursor != null && pages < 5)
        }
        val now = platform.now()
        if (error != null && all.isEmpty()) {
            _state.value = _state.value.copy(syncing = false, error = error)
            return emptyList()
        }
        _state.value = _state.value.copy(items = all, syncing = false, lastSync = now, error = error)
        store.put("notion_items", AppJson.encodeToString(all))
        store.put("notion_last_sync", now.toString())
        if (firstSync) return emptyList()
        return all.mapNotNull { item -> changeFor(before[item.id] ?: return@mapNotNull null, item) }
    }

    /** Only two things alert: one of my items went to review, or a video upload finished. */
    private fun changeFor(old: NotionItem, new: NotionItem): NotionChange? {
        if (old.edited == new.edited) return null
        val f = _state.value.filter
        val statusChanged = old.status != new.status
        if (f.notifyReview && statusChanged && statusKind(new.status) == "review") {
            return NotionChange(new, "In review: ${new.title}", "Status is now ${new.status}")
        }
        val uploadedNow = (statusChanged && new.status?.lowercase()?.contains("upload") == true) || (!old.hasVideo && new.hasVideo)
        if (f.notifyUpload && uploadedNow) {
            return NotionChange(new, "Upload complete: ${new.title}", if (new.kind == "recording") "Footage is uploaded." else "The video file is in Notion.")
        }
        return null
    }

    // ---------------- Filtering ----------------

    fun matches(item: NotionItem, f: NotionFilter, today: LocalDate): Boolean {
        if (item.jobType !in f.types) return false
        if (f.keyword.isNotBlank()) {
            val hay = "${item.title} ${item.typeRaw.orEmpty()} ${item.location.orEmpty()} ${item.status.orEmpty()}".lowercase()
            if (f.keyword.trim().lowercase() !in hay) return false
        }
        val d = item.date
        return when (f.range) {
            0 -> {
                val start = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber % 7))
                d != null && d >= today && d <= start.plus(DatePeriod(days = 6))
            }
            1 -> d != null && d >= today && d.year == today.year && d.monthNumber == today.monthNumber
            2 -> {
                val m = f.month ?: "${today.year}-${today.monthNumber.toString().padStart(2, '0')}"
                d != null && "${d.year}-${d.monthNumber.toString().padStart(2, '0')}" == m
            }
            else -> d == null || d >= today
        }
    }

    fun filtered(today: LocalDate): List<NotionItem> {
        val s = _state.value
        val cleared = _cleared.value
        val recent = today.minus(DatePeriod(days = 7))
        return s.items.filter { item ->
            if (item.id in cleared) return@filter false
            // Already-uploaded videos are hidden (the upload alert still fires when it happens).
            if (item.hasVideo || item.status?.lowercase()?.contains("upload") == true) return@filter false
            // Shoots from the last week stay until you clear them; everything else follows the filter.
            matches(item, s.filter, today) || (item.isShoot && item.date != null && item.date >= recent && item.date < today)
        }.sortedWith(compareBy<NotionItem> { it.date ?: LocalDate(9999, 12, 31) }.thenBy { it.title })
    }

    // ---------------- Clear completed shoots ----------------

    private val _cleared = MutableStateFlow(store.get("notion_cleared")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    val cleared: StateFlow<Set<String>> = _cleared.asStateFlow()

    /** A shoot is done once its date/time has passed or Notion marks it recorded. */
    fun isDone(item: NotionItem, now: Long): Boolean {
        if (!item.isShoot) return false
        val s = item.status?.lowercase().orEmpty()
        if (listOf("recorded", "notes added", "did not happen", "complete", "done").any { it in s }) return true
        val d = item.date ?: return false
        val local = toLocal(now)
        if (d < local.date) return true
        if (d > local.date) return false
        val t = item.arrival?.let(::parseClock) ?: return false
        return t <= local.time
    }

    /** Hide shoots from the list on this device (Notion itself is never changed). */
    fun clear(ids: Collection<String>) {
        val next = (_cleared.value + ids).let { all ->
            // Forget ids that no longer exist in Notion so the list doesn't grow forever.
            val live = _state.value.items.map { it.id }.toSet()
            all.filter { it in live || it in ids }.toSet()
        }
        _cleared.value = next
        store.put("notion_cleared", next.joinToString(","))
    }

    /** "4:00 PM" → 16:00. */
    private fun parseClock(text: String): kotlinx.datetime.LocalTime? {
        val m = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""").find(text) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ampm = m.groupValues[3].lowercase()
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        return runCatching { kotlinx.datetime.LocalTime(h, min) }.getOrNull()
    }

    /** When to remind about a shoot for a [NotionFilter.shootReminder] option, or null if that time has passed. */
    fun shootReminderTime(item: NotionItem, option: Int, now: Long): Long? {
        val d = item.date ?: return null
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        fun at(date: LocalDate, h: Int, m: Int = 0) =
            kotlinx.datetime.LocalDateTime(date, kotlinx.datetime.LocalTime(h, m)).toInstant(zone).toEpochMilliseconds()
        val start = item.arrival?.let(::parseClock)
        val t = when (option) {
            0 -> if (start != null) at(d, start.hour, start.minute) - 60 * 60_000L else at(d, 8)
            1 -> at(d, 8)
            2 -> at(d.minus(DatePeriod(days = 1)), 18)
            else -> return null
        }
        return t.takeIf { it > now }
    }

    // ---------------- Parsing ----------------

    private fun parsePage(page: JsonObject, dbTitle: String): NotionItem? {
        if (page["archived"]?.jsonPrimitive?.contentOrNull == "true") return null
        val props = page["properties"]?.obj ?: return null
        var title = ""
        var dateProp: Pair<String, JsonObject>? = null
        val dates = mutableListOf<Pair<String, JsonObject>>()
        var arrival: String? = null
        var type: String? = null
        var status: String? = null
        var location: String? = null
        val assignees = mutableListOf<String>()
        val tags = mutableListOf<String>()

        for ((name, el) in props) {
            val p = el.obj ?: continue
            val kind = p.str("type") ?: continue
            val n = name.lowercase()
            when (kind) {
                "title" -> title = plainText(p["title"])
                "date" -> p["date"]?.obj?.let { dates += name to it }
                "status" -> status = p["status"]?.obj?.str("name")
                "select" -> {
                    val v = p["select"]?.obj?.str("name")
                    if (v != null) when {
                        isTypeName(n) -> type = v
                        isArrivalName(n) -> arrival = v
                        n.contains("status") || n.contains("stage") -> status = status ?: v
                        type == null && looksLikeJob(v) -> type = v
                    }
                }
                "people" -> {
                    val names = p["people"]?.jsonArray?.mapNotNull { it.obj?.let { u -> u.str("name") ?: u["person"]?.obj?.str("email") } }.orEmpty()
                    // "Assigned to", "Editor", "Creator"... any people column except who created/last edited it.
                    if ("created" !in n && "edited" !in n) assignees += names
                }
                "multi_select" -> {
                    p["multi_select"]?.jsonArray?.mapNotNull { it.obj?.str("name") }?.let(tags::addAll)
                    val v = p["multi_select"]?.jsonArray?.mapNotNull { it.obj?.str("name") }?.joinToString(", ")
                    if (!v.isNullOrBlank() && (isTypeName(n) || (type == null && looksLikeJob(v)))) type = v
                }
                "rich_text" -> {
                    val v = plainText(p["rich_text"]).takeIf { it.isNotBlank() }
                    when {
                        v == null -> {}
                        isArrivalName(n) -> arrival = v
                        isLocationName(n) -> location = v
                        isTypeName(n) -> type = type ?: v
                    }
                }
                "formula" -> {
                    val v = p["formula"]?.obj?.let { it.str("string") ?: it.str("number") }
                    if (v != null && isArrivalName(n)) arrival = v
                }
                "place" -> location = location ?: p["place"]?.obj?.let { it.str("name") ?: it.str("address") }
            }
        }
        // The shoot date: a date column named like "shoot"/"date"/"when", otherwise the first date column.
        dateProp = dates.firstOrNull { (n, _) -> n.lowercase().let { "shoot" in it || "due" in it || "when" in it || n.equals("date", true) } }
            ?: dates.firstOrNull { (n, _) -> !isArrivalName(n.lowercase()) }
        val arrivalDate = dates.firstOrNull { (n, _) -> isArrivalName(n.lowercase()) }?.second
        val (date, timeFromDate) = parseDate(dateProp?.second?.str("start"))
        if (arrival == null) {
            arrival = arrivalDate?.let { parseDate(it.str("start")).second } ?: timeFromDate
        }
        return NotionItem(
            id = page.str("id") ?: return null,
            title = title.ifBlank { "Untitled" },
            date = date,
            arrival = arrival,
            typeRaw = type,
            status = status,
            location = location,
            url = page.str("url").orEmpty(),
            edited = page.str("last_edited_time").orEmpty(),
            database = dbTitle,
            assignees = assignees.distinct(),
            tags = tags.distinct(),
        )
    }

    /** "2026-11-12" → date only; "2026-11-12T07:30:00.000-05:00" → local date + "7:30 AM". */
    private fun parseDate(s: String?): Pair<LocalDate?, String?> {
        if (s.isNullOrBlank()) return null to null
        if (s.length <= 10) return runCatching { LocalDate.parse(s) }.getOrNull() to null
        return runCatching {
            val local = toLocal(Instant.parse(s).toEpochMilliseconds())
            local.date to formatTime(local.time)
        }.getOrElse { runCatching { LocalDate.parse(s.take(10)) }.getOrNull() to null }
    }

    private fun isTypeName(n: String) = listOf("type", "job", "category", "kind", "service", "topic", "deliverable").any { it in n }
    private fun isArrivalName(n: String) = listOf("arriv", "call time", "call", "start time", "time").any { it in n } && "date" !in n
    private fun isLocationName(n: String) = listOf("location", "address", "venue", "where", "place").any { it in n }
    private fun looksLikeJob(v: String) = v.lowercase().let { "video" in it || "photo" in it || "graphic" in it || "design" in it }

    private fun plainText(el: JsonElement?): String =
        (el as? JsonArray)?.joinToString("") { it.obj?.str("plain_text").orEmpty() }.orEmpty()

    private fun headers(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Notion-Version" to VERSION,
        "Content-Type" to "application/json",
    )

    private fun errorText(code: Int, body: String): String {
        val msg = runCatching { AppJson.parseToJsonElement(body).jsonObject.str("message") }.getOrNull()
        return when (code) {
            0 -> "Can't reach Notion. Check your internet connection."
            401 -> "Notion sign-in expired or the token is wrong. Please connect again."
            403, 404 -> "Focused Up can't see that database. In Notion: ••• → Connections → add Focused Up."
            429 -> "Notion is busy. Try again in a minute."
            else -> msg ?: "Notion error ($code)."
        }
    }

    private fun encode(s: String) = buildString {
        s.forEach { ch -> if (ch.isLetterOrDigit() || ch in "-._~") append(ch) else ch.toString().encodeToByteArray().forEach { append('%'); append(((it.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase()) } }
    }
}

private val JsonElement.obj: JsonObject? get() = this as? JsonObject
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
