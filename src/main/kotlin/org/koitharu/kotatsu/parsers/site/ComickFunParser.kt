package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArraySet
import androidx.collection.SparseArrayCompat
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * https://api.comick.fun/docs/static/index.html
 */

private const val PAGE_SIZE = 20
private const val CHAPTERS_LIMIT = 99999

@MangaSourceParser("COMICK_FUN", "ComicK")
internal class ComickFunParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.COMICK_FUN) {

	override val configKeyDomain = ConfigKey.Domain("comick.fun", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.RATING,
	)

	@Volatile
	private var cachedTags: SparseArrayCompat<MangaTag>? = null

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val domain = getDomain()
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/search?tachiyomi=true")
			if (!query.isNullOrEmpty()) {
				if (offset > 0) {
					return emptyList()
				}
				append("&q=")
				append(query.urlEncoded())
			} else {
				append("&limit=")
				append(PAGE_SIZE)
				append("&page=")
				append((offset / PAGE_SIZE) + 1)
				if (!tags.isNullOrEmpty()) {
					append("&genres=")
					appendAll(tags, "&genres=", MangaTag::key)
				}
				append("&sort=") // view, uploaded, rating, follow, user_follow_count
				append(
					when (sortOrder) {
						SortOrder.POPULARITY -> "view"
						SortOrder.RATING -> "rating"
						else -> "uploaded"
					},
				)
			}
		}
		val ja = context.httpGet(url).parseJsonArray()
		val tagsMap = cachedTags ?: loadTags()
		return ja.mapJSON { jo ->
			val slug = jo.getString("slug")
			Manga(
				id = generateUid(slug),
				title = jo.getString("title"),
				altTitle = null,
				url = slug,
				publicUrl = "https://$domain/comic/$slug",
				rating = jo.getDoubleOrDefault("rating", -10.0).toFloat() / 10f,
				isNsfw = false,
				coverUrl = jo.getString("cover_url"),
				largeCoverUrl = null,
				description = jo.getStringOrNull("desc"),
				tags = jo.selectGenres("genres", tagsMap),
				state = runCatching {
					if (jo.getBoolean("translation_completed")) {
						MangaState.FINISHED
					} else {
						MangaState.ONGOING
					}
				}.getOrNull(),
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val domain = getDomain()
		val url = "https://api.$domain/comic/${manga.url}?tachiyomi=true"
		val jo = context.httpGet(url).parseJson()
		val comic = jo.getJSONObject("comic")
		return manga.copy(
			title = comic.getString("title"),
			altTitle = null, // TODO
			isNsfw = jo.getBoolean("matureContent") || comic.getBoolean("hentai"),
			description = comic.getStringOrNull("parsed") ?: comic.getString("desc"),
			tags = manga.tags + jo.getJSONArray("genres").mapJSONToSet {
				MangaTag(
					title = it.getString("name"),
					key = it.getString("slug"),
					source = source,
				)
			},
			author = jo.getJSONArray("artists").optJSONObject(0)?.getString("name"),
			chapters = getChapters(comic.getLong("id")),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val jo = context.httpGet(
			"https://api.${getDomain()}/chapter/${chapter.url}?tachiyomi=true",
		).parseJson().getJSONObject("chapter")
		val referer = "https://${getDomain()}/"
		return jo.getJSONArray("images").mapJSON {
			val url = it.getString("url")
			MangaPage(
				id = generateUid(url),
				url = url,
				referer = referer,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val sparseArray = cachedTags ?: loadTags()
		val set = ArraySet<MangaTag>(sparseArray.size())
		for (i in 0 until sparseArray.size()) {
			set.add(sparseArray.valueAt(i))
		}
		return set
	}

	private suspend fun loadTags(): SparseArrayCompat<MangaTag> {
		val ja = context.httpGet("https://api.${getDomain()}/genre").parseJsonArray()
		val tags = SparseArrayCompat<MangaTag>(ja.length())
		for (jo in ja.JSONIterator()) {
			tags.append(
				jo.getInt("id"),
				MangaTag(
					title = jo.getString("name"),
					key = jo.getString("slug"),
					source = source,
				),
			)
		}
		cachedTags = tags
		return tags
	}

	private suspend fun getChapters(id: Long): List<MangaChapter> {
		val ja = context.httpGet(
			url = "https://api.${getDomain()}/comic/$id/chapter?tachiyomi=true&limit=$CHAPTERS_LIMIT",
		).parseJson().getJSONArray("chapters")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd")
		val counters = HashMap<Locale, Int>()
		return ja.mapReversed { jo ->
			val locale = Locale.forLanguageTag(jo.getString("lang"))
			var number = counters[locale] ?: 0
			number++
			counters[locale] = number
			MangaChapter(
				id = generateUid(jo.getLong("id")),
				name = buildString {
					jo.getStringOrNull("vol")?.let { append("Vol ").append(it).append(' ') }
					jo.getStringOrNull("chap")?.let { append("Chap ").append(it) }
					jo.getStringOrNull("title")?.let { append(": ").append(it) }
				},
				number = number,
				url = jo.getString("hid"),
				scanlator = jo.optJSONArray("group_name")?.optString(0),
				uploadDate = dateFormat.tryParse(jo.getString("created_at").substringBefore('T')),
				branch = locale.getDisplayName(locale).toTitleCase(locale),
				source = source,
			)
		}
	}

	private inline fun <R> JSONArray.mapReversed(block: (JSONObject) -> R): List<R> {
		val len = length()
		val destination = ArrayList<R>(len)
		for (i in (0 until len).reversed()) {
			val jo = getJSONObject(i)
			destination.add(block(jo))
		}
		return destination
	}

	private fun JSONObject.selectGenres(name: String, tags: SparseArrayCompat<MangaTag>): Set<MangaTag> {
		val array = optJSONArray(name) ?: return emptySet()
		val res = ArraySet<MangaTag>(array.length())
		for (i in 0 until array.length()) {
			val id = array.getInt(i)
			val tag = tags.get(id) ?: continue
			res.add(tag)
		}
		return res
	}
}