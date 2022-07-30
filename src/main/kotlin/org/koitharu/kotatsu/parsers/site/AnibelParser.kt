package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.stringIterator
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

@MangaSourceParser("ANIBEL", "Anibel", "be")
internal class AnibelParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.ANIBEL) {

	override val configKeyDomain = ConfigKey.Domain("anibel.net", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
	)

	override fun getFaviconUrl(): String {
		return "https://cdn.${getDomain()}/favicons/favicon.png"
	}

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			return if (offset == 0) {
				search(query)
			} else {
				emptyList()
			}
		}
		val filters = tags?.takeUnless { it.isEmpty() }?.joinToString(
			separator = ",",
			prefix = "genres: [",
			postfix = "]",
		) { "\"${it.key}\"" }.orEmpty()
		val array = apiCall(
			"""
			getMediaList(offset: $offset, limit: 20, mediaType: manga, filters: {$filters}) {
				docs {
					mediaId
					title {
						be
						alt
					}
					rating
					poster
					genres
					slug
					mediaType
					status
				}
			}
			""".trimIndent(),
		).getJSONObject("getMediaList").getJSONArray("docs")
		return array.mapJSON { jo ->
			val mediaId = jo.getString("mediaId")
			val title = jo.getJSONObject("title")
			val href = "${jo.getString("mediaType")}/${jo.getString("slug")}"
			Manga(
				id = generateUid(mediaId),
				title = title.getString("be"),
				coverUrl = jo.getString("poster").removePrefix("/cdn")
					.toAbsoluteUrl(getDomain("cdn")) + "?width=200&height=280",
				altTitle = title.getString("alt").takeUnless(String::isEmpty),
				author = null,
				isNsfw = false,
				rating = jo.getDouble("rating").toFloat() / 10f,
				url = href,
				publicUrl = "https://${getDomain()}/$href",
				tags = jo.getJSONArray("genres").mapToTags(),
				state = when (jo.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"finished" -> MangaState.FINISHED
					else -> null
				},
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val (type, slug) = manga.url.split('/')
		val details = apiCall(
			"""
			media(mediaType: $type, slug: "$slug") {
				mediaId
				title {
					be
					alt
				}
				description {
					be
				}
				status
				poster
				rating
				genres
			}
			""".trimIndent(),
		).getJSONObject("media")
		val title = details.getJSONObject("title")
		val poster = details.getString("poster").removePrefix("/cdn")
			.toAbsoluteUrl(getDomain("cdn"))
		val chapters = apiCall(
			"""
			chapters(mediaId: "${details.getString("mediaId")}") {
				id
				chapter
				released
			}
			""".trimIndent(),
		).getJSONArray("chapters")
		return manga.copy(
			title = title.getString("be"),
			altTitle = title.getString("alt"),
			coverUrl = "$poster?width=200&height=280",
			largeCoverUrl = poster,
			description = details.getJSONObject("description").getString("be"),
			rating = details.getDouble("rating").toFloat() / 10f,
			tags = details.getJSONArray("genres").mapToTags(),
			state = when (details.getString("status")) {
				"ongoing" -> MangaState.ONGOING
				"finished" -> MangaState.FINISHED
				else -> null
			},
			chapters = chapters.mapJSON { jo ->
				val number = jo.getInt("chapter")
				MangaChapter(
					id = generateUid(jo.getString("id")),
					name = "Глава $number",
					number = number,
					url = "${manga.url}/read/$number",
					scanlator = null,
					uploadDate = jo.getLong("released"),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val (_, slug, _, number) = chapter.url.split('/')
		val chapterJson = apiCall(
			"""
			chapter(slug: "$slug", chapter: $number) {
				id
				images {
					large
					thumbnail
				}
			}
			""".trimIndent(),
		).getJSONObject("chapter")
		val pages = chapterJson.getJSONArray("images")
		val chapterUrl = "https://${getDomain()}/${chapter.url}"
		return pages.mapJSONIndexed { i, jo ->
			MangaPage(
				id = generateUid("${chapter.url}/$i"),
				url = jo.getString("large"),
				referer = chapterUrl,
				preview = jo.getString("thumbnail"),
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val json = apiCall(
			"""
			getFilters(mediaType: manga) {
				genres
			}
			""".trimIndent(),
		)
		val array = json.getJSONObject("getFilters").getJSONArray("genres")
		return array.mapToTags()
	}

	private suspend fun search(query: String): List<Manga> {
		val json = apiCall(
			"""
			search(query: "$query", limit: 40) {
				id
				title {
					be
					en
				}
				poster
				url
				type
			}
			""".trimIndent(),
		)
		val array = json.getJSONArray("search")
		return array.mapJSON { jo ->
			val mediaId = jo.getString("id")
			val title = jo.getJSONObject("title")
			val href = "${jo.getString("type").lowercase()}/${jo.getString("url")}"
			Manga(
				id = generateUid(mediaId),
				title = title.getString("be"),
				coverUrl = jo.getString("poster").removePrefix("/cdn")
					.toAbsoluteUrl(getDomain("cdn")) + "?width=200&height=280",
				altTitle = title.getString("en").takeUnless(String::isEmpty),
				author = null,
				isNsfw = false,
				rating = RATING_UNKNOWN,
				url = href,
				publicUrl = "https://${getDomain()}/$href",
				tags = emptySet(),
				state = null,
				source = source,
			)
		}
	}

	private suspend fun apiCall(request: String): JSONObject {
		return context.graphQLQuery("https://api.${getDomain()}/graphql", request)
			.getJSONObject("data")
	}

	private fun JSONArray.mapToTags(): Set<MangaTag> {

		fun toTitle(slug: String): String {
			val builder = StringBuilder(slug)
			var capitalize = true
			for ((i, c) in builder.withIndex()) {
				when {
					c == '-' -> {
						builder.setCharAt(i, ' ')
					}
					capitalize -> {
						builder.setCharAt(i, c.uppercaseChar())
						capitalize = false
					}
				}
			}
			return builder.toString()
		}

		val result = ArraySet<MangaTag>(length())
		stringIterator().forEach {
			result.add(
				MangaTag(
					title = toTitle(it),
					key = it,
					source = source,
				),
			)
		}
		return result
	}
}