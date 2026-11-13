package com.focusedup.shared.data

import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.util.formatTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Reads Notion with the user's own sign-in (the same way notion.so does in a browser).
 * Works for guests, who can't add official integrations. Read-only: it never writes anything.
 */
class NotionWebClient(private val platform: FocusPlatform, private val cookie: String) {
    class Expired : Exception()

    private val base = "https://app.notion.com/api/v3/"
    private val userNames = mutableMapOf<String, String>()

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val userId = Regex("notion_user_id=([^;]+)").find(cookie)?.groupValues?.get(1)
        val headers = buildMap {
            put("Cookie", cookie)
            put("Content-Type", "application/json")
            if (userId != null) put("x-notion-active-user-header", userId)
        }
        val res = platform.http("POST", base + path, headers, body.toString())
        if (res.code == 401 || res.code == 403) throw Expired()
        if (res.code != 200) error(if (res.code == 0) "Can't reach Notion. Check your internet connection." else "Notion error (${res.code}).")
        return AppJson.parseToJsonElement(res.body).jsonObject
    }

    /** Record values come wrapped as {value:{value:{...}}} or {value:{...}}. */
    private fun JsonElement?.record(): JsonObject? {
        val v = (this as? JsonObject)?.get("value") as? JsonObject ?: return null
        return (v["value"] as? JsonObject) ?: v
    }

    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    suspend fun whoAmI(): String {
        val j = post("loadUserContent", buildJsonObject { })
        val users = j["recordMap"]?.jsonObject?.get("notion_user") as? JsonObject
        return users?.values?.firstOrNull().record()?.s("name").orEmpty()
    }

    /** The signed-in user's id and name. */
    suspend fun me(): Pair<String, String> {
        val j = post("loadUserContent", buildJsonObject { })
        val users = j["recordMap"]?.jsonObject?.get("notion_user") as? JsonObject
        val (id, el) = users?.entries?.firstOrNull() ?: return "" to ""
        return id to el.record()?.s("name").orEmpty().trim()
    }

    private suspend fun blocks(ids: Collection<String>): Map<String, JsonObject> {
        if (ids.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, JsonObject>()
        ids.chunked(80).forEach { chunk ->
            val j = runCatching {
                post("syncRecordValues", buildJsonObject {
                    putJsonArray("requests") {
                        chunk.forEach { id -> addJsonObject { putJsonObject("pointer") { put("table", "block"); put("id", id) }; put("version", -1) } }
                    }
                })
            }.getOrNull() ?: return@forEach
            (j["recordMap"]?.jsonObject?.get("block") as? JsonObject)?.forEach { (id, el) -> el.record()?.let { out[id] = it } }
        }
        return out
    }

    private suspend fun collectionNames(ids: Collection<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val j = runCatching {
            post("syncRecordValues", buildJsonObject {
                putJsonArray("requests") {
                    ids.forEach { id -> addJsonObject { putJsonObject("pointer") { put("table", "collection"); put("id", id) }; put("version", -1) } }
                }
            })
        }.getOrNull() ?: return emptyMap()
        return (j["recordMap"]?.jsonObject?.get("collection") as? JsonObject)?.mapNotNull { (id, el) ->
            el.record()?.get("name")?.let(::richText)?.let { id to it }
        }?.toMap().orEmpty()
    }

    /**
     * Only the databases used inside "<name>'s Workspace" (its sub-pages, linked pages like
     * "To Record", and the lists embedded in them). Everything else in Notion is ignored.
     */
    suspend fun workspaceDatabases(myName: String): List<NotionDatabase> {
        val content = post("loadUserContent", buildJsonObject { })
        val sidebar = (content["recordMap"]?.jsonObject?.get("block") as? JsonObject)?.values?.mapNotNull { it.record() }.orEmpty()
        fun norm(s: String) = s.lowercase().replace('’', '\'').trim()
        val first = norm(myName).substringBefore(' ')
        val root = sidebar.firstOrNull { v ->
            val t = norm(v["properties"]?.jsonObject?.get("title")?.let(::richText).orEmpty())
            t.contains("workspace") && (first.isEmpty() || t.startsWith(first))
        }?.s("id") ?: return emptyList()

        val seen = mutableSetOf<String>()
        val found = linkedMapOf<String, NotionDatabase>()
        var frontier = listOf(root)
        var depth = 0
        while (frontier.isNotEmpty() && depth < 6 && seen.size < 600) {
            val recs = blocks(frontier.filter { seen.add(it) })
            val next = mutableListOf<String>()
            for ((id, v) in recs) {
                when (v.s("type")) {
                    "collection_view", "collection_view_page" -> {
                        val cid = v.s("collection_id") ?: (v["format"] as? JsonObject)?.get("collection_pointer")?.jsonObject?.s("id")
                        val view = (v["view_ids"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
                        val space = v.s("space_id").orEmpty()
                        if (cid != null && view != null && cid !in found) found[cid] = NotionDatabase(cid, "", view, space)
                    }
                    "alias" -> (v["format"] as? JsonObject)?.get("alias_pointer")?.jsonObject?.s("id")?.let(next::add)
                    else -> {
                        (v["content"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.let(next::addAll)
                        // Page mentions in text (e.g. the "To Record" link).
                        mentions(v["properties"]?.jsonObject?.get("title")).forEach(next::add)
                    }
                }
                if (id == root) Unit
            }
            frontier = next.distinct()
            depth++
        }
        val names = collectionNames(found.keys)
        return found.values.map { it.copy(title = names[it.id] ?: "Untitled") }
            .filterNot { d -> listOf("invoice", "flag").any { it in d.title.lowercase() } }
    }

    private fun mentions(el: JsonElement?): List<String> =
        (el as? JsonArray)?.flatMap { seg ->
            ((seg as? JsonArray)?.getOrNull(1) as? JsonArray)?.mapNotNull { m ->
                val a = m as? JsonArray
                if ((a?.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "p") (a.getOrNull(1) as? JsonPrimitive)?.contentOrNull else null
            }.orEmpty()
        }.orEmpty()

    /** Every database the user can see, across their own and shared (guest) workspaces. */
    suspend fun discover(): List<NotionDatabase> {
        val content = post("loadUserContent", buildJsonObject { })
        val spaceViews = content["recordMap"]?.jsonObject?.get("space_view") as? JsonObject
        val spaces = spaceViews?.values?.mapNotNull { it.record()?.s("space_id") }?.distinct().orEmpty()
        val out = mutableListOf<NotionDatabase>()
        for (space in spaces) {
            val j = runCatching {
                post("search", buildJsonObject {
                    put("type", "BlocksInSpace")
                    put("query", "")
                    put("spaceId", space)
                    put("limit", 100)
                    putJsonObject("filters") {
                        put("isDeletedOnly", false); put("excludeTemplates", true); put("navigableBlockContentOnly", true)
                        put("requireEditPermissions", false); put("includePublicPagesWithoutExplicitAccess", false)
                        putJsonArray("ancestors") {}; putJsonArray("createdBy") {}; putJsonArray("editedBy") {}
                        putJsonObject("lastEditedTime") {}; putJsonObject("createdTime") {}; putJsonArray("inTeams") {}
                    }
                    putJsonObject("sort") { put("field", "relevance") }
                    put("source", "quick_find")
                })
            }.getOrNull() ?: continue
            val rm = j["recordMap"]?.jsonObject ?: continue
            val collections = rm["collection"] as? JsonObject
            (rm["block"] as? JsonObject)?.values?.forEach { b ->
                val v = b.record() ?: return@forEach
                val type = v.s("type")
                if (type != "collection_view_page" && type != "collection_view") return@forEach
                val cid = v.s("collection_id") ?: return@forEach
                val view = (v["view_ids"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@forEach
                val name = collections?.get(cid).record()?.get("name")?.let(::richText).orEmpty().ifBlank { "Untitled database" }
                if (out.none { it.id == cid }) out += NotionDatabase(cid, name, view, space)
            }
        }
        return out.sortedBy { it.title }
    }

    /** All rows of one database, mapped to NotionItems. */
    /** Rows of one database. With [myId] set, only rows assigned to that person (or naming them as editor/creator). */
    suspend fun items(db: NotionDatabase, myId: String = "", myName: String = ""): List<NotionItem> {
        val schemaJson = post("syncRecordValues", buildJsonObject {
            putJsonArray("requests") {
                addJsonObject {
                    putJsonObject("pointer") { put("table", "collection"); put("id", db.id); put("spaceId", db.space) }
                    put("version", -1)
                }
            }
        })
        val schema = schemaJson["recordMap"]?.jsonObject?.get("collection")?.jsonObject?.get(db.id).record()?.get("schema") as? JsonObject
            ?: return emptyList()
        val j = post("queryCollection", buildJsonObject {
            putJsonObject("collection") { put("id", db.id); put("spaceId", db.space) }
            putJsonObject("collectionView") { put("id", db.view); put("spaceId", db.space) }
            putJsonObject("loader") {
                put("type", "reducer")
                putJsonObject("reducers") { putJsonObject("collection_group_results") { put("type", "results"); put("limit", 500) } }
                put("searchQuery", "")
                put("userTimeZone", kotlinx.datetime.TimeZone.currentSystemDefault().id)
            }
        })
        val ids = (j["result"]?.jsonObject?.get("reducerResults")?.jsonObject?.get("collection_group_results")?.jsonObject?.get("blockIds") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val blocks = j["recordMap"]?.jsonObject?.get("block") as? JsonObject ?: return emptyList()
        val rows = ids.mapNotNull { blocks[it].record() }.filter { it["alive"]?.let { a -> (a as? JsonPrimitive)?.contentOrNull } != "false" }
        resolveUsers(rows, schema)
        val mine = if (myId.isBlank()) rows else rows.filter { isMine(it, schema, myId, myName) }
        return mine.mapNotNull { parse(it, schema, db.title) }
    }

    private fun isMine(row: JsonObject, schema: JsonObject, myId: String, myName: String): Boolean {
        val props = row["properties"] as? JsonObject ?: return false
        val first = myName.substringBefore(' ').lowercase()
        return schema.any { (key, def) ->
            val d = def as? JsonObject ?: return@any false
            val n = d.s("name").orEmpty().lowercase()
            when (d.s("type")) {
                "person" -> "created" !in n && myId in userIds(props[key])
                "text" -> first.isNotEmpty() && listOf("edited by", "editor", "creator", "assigned").any { it in n } &&
                    richText(props[key]).lowercase().contains(first)
                else -> false
            }
        }
    }

    private suspend fun resolveUsers(rows: List<JsonObject>, schema: JsonObject) {
        val personKeys = schema.filter { (_, v) -> (v as? JsonObject)?.s("type") == "person" }.keys
        val ids = mutableSetOf<String>()
        rows.forEach { r -> personKeys.forEach { k -> userIds(r["properties"]?.jsonObject?.get(k)).forEach(ids::add) } }
        val missing = ids - userNames.keys
        if (missing.isEmpty()) return
        val j = runCatching {
            post("syncRecordValues", buildJsonObject {
                putJsonArray("requests") {
                    missing.forEach { id -> addJsonObject { putJsonObject("pointer") { put("table", "notion_user"); put("id", id) }; put("version", -1) } }
                }
            })
        }.getOrNull() ?: return
        (j["recordMap"]?.jsonObject?.get("notion_user") as? JsonObject)?.forEach { (id, el) ->
            el.record()?.s("name")?.let { userNames[id] = it.trim() }
        }
    }

    private fun userIds(prop: JsonElement?): List<String> =
        (prop as? JsonArray)?.flatMap { seg ->
            ((seg as? JsonArray)?.getOrNull(1) as? JsonArray)?.mapNotNull { m ->
                val a = m as? JsonArray
                if ((a?.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "u") (a.getOrNull(1) as? JsonPrimitive)?.contentOrNull else null
            }.orEmpty()
        }.orEmpty()

    /** Plain text of a Notion rich-text value. */
    private fun richText(el: JsonElement?): String =
        (el as? JsonArray)?.joinToString("") { seg -> ((seg as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()

    /** The date annotation inside a date property: {start_date, start_time, ...}. */
    private fun dateOf(el: JsonElement?): JsonObject? =
        (el as? JsonArray)?.firstNotNullOfOrNull { seg ->
            ((seg as? JsonArray)?.getOrNull(1) as? JsonArray)?.firstNotNullOfOrNull { m ->
                val a = m as? JsonArray
                if ((a?.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "d") a.getOrNull(1) as? JsonObject else null
            }
        }

    private fun parse(row: JsonObject, schema: JsonObject, dbTitle: String): NotionItem? {
        val props = row["properties"] as? JsonObject ?: JsonObject(emptyMap())
        var title = ""
        var fallbackTitle: String? = null
        var status: String? = null
        var payment: String? = null
        var type: String? = null
        var location: String? = null
        var arrival: String? = null
        val dates = mutableListOf<Pair<String, JsonObject>>()
        val assignees = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var hasVideo = false
        for ((key, def) in schema) {
            val d = def as? JsonObject ?: continue
            val name = d.s("name").orEmpty()
            val n = name.lowercase()
            val value = props[key]
            when (d.s("type")) {
                "title" -> title = richText(value)
                "status" -> richText(value).takeIf { it.isNotBlank() }?.let { if ("payment" in n) payment = it else status = it }
                "select" -> richText(value).takeIf { it.isNotBlank() }?.let { v ->
                    when {
                        "type" in n || "job" in n || "category" in n -> type = v
                        "status" in n || "stage" in n -> status = status ?: v
                        "arriv" in n || "call" in n -> arrival = v
                    }
                }
                "multi_select" -> richText(value).split(',').map { it.trim() }.filter { it.isNotEmpty() }.let(tags::addAll)
                "person" -> if ("created" !in n && "edited" !in n) userIds(value).forEach { id -> userNames[id]?.takeIf { it.isNotBlank() }?.let { assignees += it } }
                "date" -> dateOf(value)?.let { dates += n to it }
                "file" -> if ("video" in n && richText(value).isNotBlank()) hasVideo = true
                "text" -> richText(value).takeIf { it.isNotBlank() }?.let { v ->
                    when {
                        "arriv" in n || "call time" in n -> arrival = v
                        "location" in n || "address" in n || "venue" in n -> location = v
                        "edited by" in n || "editor" in n -> assignees += v
                        "hook" in n -> fallbackTitle = v.lines().first().take(80)
                        fallbackTitle == null && ("caption" in n || "outline" in n) -> fallbackTitle = v.lines().first().take(80)
                    }
                }
            }
        }
        val main = dates.firstOrNull { "shoot" in it.first } ?: dates.firstOrNull { "post" in it.first }
            ?: dates.firstOrNull { "due" in it.first } ?: dates.firstOrNull()
        val date = main?.second?.s("start_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (arrival == null) {
            arrival = main?.second?.s("start_time")?.let { t -> runCatching { formatTime(LocalTime.parse(t)) }.getOrNull() }
        }
        val id = row.s("id") ?: return null
        // Payment "Needs Payment" counts as finished when the main status doesn't say otherwise.
        val effectiveStatus = status ?: payment
        return NotionItem(
            id = id,
            // Titles that are just a link/mention come through as "‣"; use the hook or caption instead.
            title = title.replace("‣", "").trim().ifBlank { fallbackTitle ?: dbTitle.ifBlank { "Untitled" } },
            date = date,
            arrival = arrival,
            typeRaw = type,
            status = effectiveStatus,
            location = location,
            url = "https://www.notion.so/" + id.replace("-", ""),
            edited = ((row["last_edited_time"] as? JsonPrimitive)?.longOrNull ?: 0L).toString(),
            database = dbTitle,
            assignees = assignees.distinct(),
            tags = tags.distinct(),
            hasVideo = hasVideo,
            kind = when {
                "shoot log" in dbTitle.lowercase() -> "recording"
                "task" in dbTitle.lowercase() -> "task"
                else -> "content"
            },
        )
    }
}
