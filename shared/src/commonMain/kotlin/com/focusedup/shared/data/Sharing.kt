package com.focusedup.shared.data

import com.focusedup.shared.AppConfig
import com.focusedup.shared.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The part of a profile that travels in a share link (photos stay on the device). */
@Serializable
data class SharedProfile(
    val n: String,
    val t: String = "",
    val c: Int = 0,
    /** Prayer streak. */
    val s: Int = 0,
    /** Total focus minutes. */
    val m: Int = 0,
)

@OptIn(ExperimentalEncodingApi::class)
object ShareLinks {
    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** https link: opens the app if installed, otherwise the page asks before offering the download. */
    fun build(p: SharedProfile): String {
        val payload = b64.encode(AppJson.encodeToString(p).encodeToByteArray())
        val base = AppConfig.SHARE_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        return "${base}#p=$payload"
    }

    /** Accepts the https link, a focusedup://profile link, or just the code. */
    fun parse(link: String): SharedProfile? {
        val code = link.trim().substringAfter("p=", link.trim()).substringBefore('&').substringBefore(' ')
        return runCatching { AppJson.decodeFromString<SharedProfile>(b64.decode(code).decodeToString()) }.getOrNull()
            ?.takeIf { it.n.isNotBlank() }
    }
}

/** Profiles other people shared with you. */
class FriendsStore(private val store: KeyValueStore) {
    private val _list = MutableStateFlow(
        store.get("friends")?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { AppJson.decodeFromString<List<SharedProfile>>(it) }.getOrNull() } ?: emptyList()
    )
    val list: StateFlow<List<SharedProfile>> = _list.asStateFlow()

    fun add(p: SharedProfile) = save(_list.value.filterNot { it.n == p.n } + p)
    fun remove(p: SharedProfile) = save(_list.value - p)

    private fun save(l: List<SharedProfile>) {
        _list.value = l
        store.put("friends", AppJson.encodeToString(l))
    }
}
