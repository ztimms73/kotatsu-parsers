package org.koitharu.kotatsu.parsers.util

import androidx.collection.SparseArrayCompat
import androidx.collection.set

class Paginator constructor(private val initialPageSize: Int) {

	var firstPage = 1
	private var pages = SparseArrayCompat<Int>()

	fun getPage(offset: Int): Int {
		if (offset == 0) { // just an optimization
			return firstPage
		}
		pages[offset]?.let { return it }
		val pageSize = initialPageSize
		val intPage = offset / pageSize
		val tail = offset % pageSize
		return intPage + firstPage + if (tail == 0) 0 else 1
	}

	fun onListReceived(offset: Int, page: Int, count: Int) {
		pages[offset + count] = if (count > 0) page + 1 else page
	}
}