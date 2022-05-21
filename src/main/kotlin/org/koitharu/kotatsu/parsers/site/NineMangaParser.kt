package org.koitharu.kotatsu.parsers.site

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 26

internal abstract class NineMangaParser(
	final override val context: MangaLoaderContext,
	source: MangaSource,
	defaultDomain: String,
) : MangaParser(source) {

	override val configKeyDomain = ConfigKey.Domain(defaultDomain, null)

	init {
		context.cookieJar.insertCookies(getDomain(), "ninemanga_template_desk=yes")
	}

	private val headers = Headers.Builder()
		.add("Accept-Language", "en-US;q=0.7,en;q=0.3")
		.build()

	override val sortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.POPULARITY,
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val page = (offset / PAGE_SIZE.toFloat()).toIntUp() + 1
		val url = buildString {
			append("https://")
			append(getDomain())
			when {
				!query.isNullOrEmpty() -> {
					append("/search/?name_sel=&wd=")
					append(query.urlEncoded())
					append("&page=")
				}
				!tags.isNullOrEmpty() -> {
					append("/search/?category_id=")
					for (tag in tags) {
						append(tag.key)
						append(',')
					}
					append("&page=")
				}
				else -> {
					append("/category/index_")
				}
			}
			append(page)
			append(".html")
		}
		val doc = context.httpGet(url, headers).parseHtml()
		val root = doc.body().selectFirst("ul.direlist")
			?: throw ParseException("Cannot find root")
		val baseHost = root.baseUri().toHttpUrl().host
		return root.select("li").map { node ->
			val href = node.selectFirst("a")?.absUrl("href")
				?: parseFailed("Link not found")
			val relUrl = href.toRelativeUrl(baseHost)
			val dd = node.selectFirst("dd")
			Manga(
				id = generateUid(relUrl),
				url = relUrl,
				publicUrl = href,
				title = dd?.selectFirst("a.bookname")?.text()?.toCamelCase().orEmpty(),
				altTitle = null,
				coverUrl = node.selectFirst("img")?.absUrl("src").orEmpty(),
				rating = RATING_UNKNOWN,
				author = null,
				isNsfw = false,
				tags = emptySet(),
				state = null,
				source = source,
				description = dd?.selectFirst("p")?.html(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(
			manga.url.withDomain() + "?waring=1",
			headers,
		).parseHtml()
		val root = doc.body().selectFirst("div.manga")
			?: throw ParseException("Cannot find root")
		val infoRoot = root.selectFirst("div.bookintro")
			?: throw ParseException("Cannot find info")
		return manga.copy(
			tags = infoRoot.getElementsByAttributeValue("itemprop", "genre").first()
				?.select("a")?.mapToSet { a ->
					MangaTag(
						title = a.text().toTitleCase(),
						key = a.attr("href").substringBetween("/", "."),
						source = source,
					)
				}.orEmpty(),
			author = infoRoot.getElementsByAttributeValue("itemprop", "author").first()?.text(),
			state = parseStatus(infoRoot.select("li a.red").text()),
			description = infoRoot.getElementsByAttributeValue("itemprop", "description").first()
				?.html()?.substringAfter("</b>"),
			chapters = root.selectFirst("div.chapterbox")?.select("ul.sub_vol_ul > li")
				?.asReversed()?.mapIndexed { i, li ->
					val a = li.selectFirst("a.chapter_list_a")
					val href = a?.attrAsRelativeUrlOrNull("href")
						?.replace("%20", " ") ?: parseFailed("Link not found")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1,
						url = href,
						uploadDate = parseChapterDateByLang(li.selectFirst("span")?.text().orEmpty()),
						source = source,
						scanlator = null,
						branch = null,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = context.httpGet(chapter.url.withDomain(), headers).parseHtml()
		return doc.body().getElementById("page")?.select("option")?.map { option ->
			val url = option.attr("value")
			MangaPage(
				id = generateUid(url),
				url = url,
				referer = chapter.url.withDomain(),
				preview = null,
				source = source,
			)
		} ?: throw ParseException("Pages list not found at ${chapter.url}")
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = context.httpGet(page.url.withDomain(), headers).parseHtml()
		val root = doc.body()
		return root.selectFirst("a.pic_download")?.absUrl("href")
			?: throw ParseException("Page image not found")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}/search/?type=high", headers)
			.parseHtml()
		val root = doc.body().getElementById("search_form")
		return root?.select("li.cate_list")?.mapNotNullToSet { li ->
			val cateId = li.attr("cate_id") ?: return@mapNotNullToSet null
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			MangaTag(
				title = a.text().toTitleCase(),
				key = cateId,
				source = source,
			)
		} ?: parseFailed("Root not found")
	}

	private fun parseStatus(status: String) = when {
		status.contains("Ongoing") -> MangaState.ONGOING
		status.contains("Completed") -> MangaState.FINISHED
		else -> null
	}

	private fun parseChapterDateByLang(date: String): Long {
		val dateWords = date.split(" ")

		if (dateWords.size == 3) {
			if (dateWords[1].contains(",")) {
				SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).tryParse(date)
			} else {
				val timeAgo = Integer.parseInt(dateWords[0])
				return Calendar.getInstance().apply {
					when (dateWords[1]) {
						"minutes" -> Calendar.MINUTE // EN-FR
						"hours" -> Calendar.HOUR // EN

						"minutos" -> Calendar.MINUTE // ES
						"horas" -> Calendar.HOUR

						// "minutos" -> Calendar.MINUTE // BR
						"hora" -> Calendar.HOUR

						"минут" -> Calendar.MINUTE // RU
						"часа" -> Calendar.HOUR

						"Stunden" -> Calendar.HOUR // DE

						"minuti" -> Calendar.MINUTE // IT
						"ore" -> Calendar.HOUR

						"heures" -> Calendar.HOUR // FR ("minutes" also French word)
						else -> null
					}?.let {
						add(it, -timeAgo)
					}
				}.timeInMillis
			}
		}
		return 0L
	}

	@MangaSourceParser("NINEMANGA_EN", "NineManga English", "en")
	class English(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_EN,
		"www.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_ES", "NineManga Español", "es")
	class Spanish(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_ES,
		"es.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_RU", "NineManga Русский", "ru")
	class Russian(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_RU,
		"ru.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_DE", "NineManga Deutsch", "de")
	class Deutsch(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_DE,
		"de.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_BR", "NineManga Brasil", "pt")
	class Brazil(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_BR,
		"br.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_IT", "NineManga Italiano", "it")
	class Italiano(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_IT,
		"it.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_FR", "NineManga Français", "fr")
	class Francais(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_FR,
		"fr.ninemanga.com",
	)
}