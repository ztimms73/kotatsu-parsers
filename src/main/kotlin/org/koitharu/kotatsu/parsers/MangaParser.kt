package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

abstract class MangaParser @InternalParsersApi constructor(val source: MangaSource) {

	protected abstract val context: MangaLoaderContext

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	abstract val sortOrders: Set<SortOrder>

	val config by lazy { context.getConfig(source) }

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [getDomain] instead.
	 */
	protected abstract val configKeyDomain: ConfigKey.Domain

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param query search query, may be null or empty if no search needed
	 * @param tags genres for filtering, values from [getTags] and [Manga.tags]. May be null or empty
	 * @param sortOrder one of [sortOrders] or null for default value
	 */
	@InternalParsersApi
	abstract suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga>

	suspend fun getList(offset: Int, query: String?): List<Manga> {
		return getList(offset, query, null, getDefaultSortOrder())
	}

	suspend fun getList(offset: Int, tags: Set<MangaTag>?, sortOrder: SortOrder?): List<Manga> {
		return getList(offset, null, tags, sortOrder ?: getDefaultSortOrder())
	}

	/**
	 * Parse details for [Manga]: chapters list, description, large cover, etc.
	 * Must return the same manga, may change any fields excepts id, url and source
	 * @see Manga.copy
	 */
	abstract suspend fun getDetails(manga: Manga): Manga

	/**
	 * Parse pages list for specified chapter.
	 * @see MangaPage for details
	 */
	abstract suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	/**
	 * Fetch direct link to the page image.
	 */
	open suspend fun getPageUrl(page: MangaPage): String = page.url.withDomain()

	/**
	 * Fetch available tags (genres) for source
	 */
	abstract suspend fun getTags(): Set<MangaTag>

	/**
	 * Returns direct link to the website favicon
	 */
	open fun getFaviconUrl() = "https://${getDomain()}/favicon.ico"

	@CallSuper
	open fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	/* Utils */

	fun getDomain(): String {
		return config[configKeyDomain]
	}

	/**
	 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
	 * @param url must be relative url, without a domain
	 * @see [Manga.id]
	 * @see [MangaChapter.id]
	 * @see [MangaPage.id]
	 */
	@InternalParsersApi
	protected fun generateUid(url: String): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		url.forEach { c ->
			h = 31 * h + c.code
		}
		return h
	}

	/**
	 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
	 * @param id an internal identifier
	 * @see [Manga.id]
	 * @see [MangaChapter.id]
	 * @see [MangaPage.id]
	 */
	@InternalParsersApi
	protected fun generateUid(id: Long): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		h = 31 * h + id
		return h
	}

	/**
	 * Convert relative url to an absolute using [getDomain]
	 */
	protected fun String.withDomain(subdomain: String? = null): String {
		var domain = getDomain()
		if (subdomain != null) {
			domain = subdomain + "." + domain.removePrefix("www.")
		}
		return toAbsoluteUrl(domain)
	}

	private fun getDefaultSortOrder(): SortOrder {
		return checkNotNull(sortOrders.minByOrNull { it.ordinal }) {
			"sortOrders should have at least one value"
		}
	}

	@InternalParsersApi
	@Suppress("NOTHING_TO_INLINE")
	protected inline fun parseFailed(message: String? = null): Nothing {
		throw ParseException(message, null)
	}
}