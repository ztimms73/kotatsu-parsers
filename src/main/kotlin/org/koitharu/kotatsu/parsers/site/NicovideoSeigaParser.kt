package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toIntUp
import java.util.*

@MangaSourceParser("NICOVIDEOSEIGA", "Nicovideo Seiga", "ja")
class NicovideoSeigaParser(override val context: MangaLoaderContext) : MangaParser(MangaSource.NICOVIDEOSEIGA) {

	override val sortOrders: Set<SortOrder>
		get() = Collections.singleton(SortOrder.UPDATED)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("seiga.nicovideo.jp", null)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		val page = (offset / 20f).toIntUp().inc()
		val url = "/manga/list?page=$page&sort=manga_updated".withDomain()
		val doc = context.httpGet(url).parseHtml()
		val comicList = doc.body().select("#comic_list > ul > li") ?: parseFailed("Container not found")
		val items = comicList.select("div > .description > div > div")
		return items.mapNotNull { item ->
			val href = item.select(".comic_icon > div > a").attr("href") ?: return@mapNotNull null
			val statusText = item.select(".mg_description_header > .mg_icon > .content_status > span").text()
			Manga(
				id = generateUid(href),
				title = item.select(".mg_body > .title > a").text() ?: return@mapNotNull null,
				coverUrl = item.select(".comic_icon > div > a > img").attr("src"),
				altTitle = null,
				author = item.select(".mg_description_header > .mg_author > a").text() ?: return@mapNotNull null,
				rating = 0F,
				url = href,
				isNsfw = false,
				tags = emptySet(),
				state = when (statusText) {
					"連載" -> MangaState.ONGOING
					"完結" -> MangaState.FINISHED
					else -> null
				},
				publicUrl = href.toAbsoluteUrl(item.host ?: getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.withDomain()).parseHtml()
		val contents = doc.body().select("#contents") ?: parseFailed("Cannot find root")
		val statusText = contents.select("div.mg_work_detail > div > div:nth-child(2) > div.tip.content_status.status_series > span").text()
		return manga.copy(
			description = contents.select("div.mg_work_detail > div > div.row > div.description_text").text(),
			largeCoverUrl = contents.select("div.primaries > div.main_visual > a > img").attr("src"),
			state = when (statusText) {
				"連載" -> MangaState.ONGOING
				"完結" -> MangaState.FINISHED
				else -> null
			},
			chapters = contents.select("#episode_list > ul > li").mapIndexedNotNull { i, li ->
				val href = li.select("div > div.description > div.title > a").attr("href").withDomain()
				MangaChapter(
					id = generateUid(href),
					name = li.select("div > div.description > div.title > a").text(),
					number = i + 1,
					url = href,
					scanlator = null,
					branch = null,
					uploadDate = 0,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		TODO("Not yet implemented")
	}

	override suspend fun getTags(): Set<MangaTag> {
		TODO("Not yet implemented")
	}
}