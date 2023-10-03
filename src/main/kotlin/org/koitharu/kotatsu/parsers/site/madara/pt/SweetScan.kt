package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SWEETSCAN", "Sweet Scan", "pt")
internal class SweetScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SWEETSCAN, "sweetscan.net") {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
