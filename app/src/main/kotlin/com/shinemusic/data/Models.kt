package com.shinemusic.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val image: String,
    val url: String,
    val durationSec: Int = 0
)

data class LyricLine(val timeMs: Long, val text: String)

private val tagRegex = Regex("""[(\[](from|original|film|motion|official|full)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val symbolRegex = Regex("""[^\p{L}\p{N} ]""")
private val spaceRegex = Regex("""\s+""")

/** lower-case, letters/digits only, single spaces (works for Hindi / Punjabi script too) */
fun normText(s: String): String = s.lowercase().replace(symbolRegex, " ").replace(spaceRegex, " ").trim()

/** normText + "(From ...)", "(Original ...)" style tags removed */
fun normTitle(s: String): String = normText(s.replace(tagRegex, " "))

/**
 * Identity of a song regardless of which album/id it was listed under.
 * The same track often shows up with several ids ("Hass Hass", "Hass Hass (From ...)"),
 * which is what made the same song appear more than once.
 */
fun Song.key(): String = normTitle(title) + "|" + normText(artist.split(",").firstOrNull().orEmpty())

/** Drop repeated ids and repeated tracks, keeping the first occurrence and the original order. */
fun List<Song>.uniqueSongs(): List<Song> {
    val ids = HashSet<String>()
    val keys = HashSet<String>()
    return filter { ids.add(it.id) && keys.add(it.key()) }
}
