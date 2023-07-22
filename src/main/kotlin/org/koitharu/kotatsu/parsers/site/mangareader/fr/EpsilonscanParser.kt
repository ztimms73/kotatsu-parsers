package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("EPSILONSCAN", "Epsilonscan", "fr")
internal class EpsilonscanParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.EPSILONSCAN, "epsilonscan.fr", pageSize = 20, searchPageSize = 10) {

	override val isNsfwSource: Boolean = true
}
