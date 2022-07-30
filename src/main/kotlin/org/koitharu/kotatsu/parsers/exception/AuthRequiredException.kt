package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Authorization is required for access to the requested content
 */
class AuthRequiredException @InternalParsersApi constructor(
	val source: MangaSource,
) : RuntimeException("Authorization required")