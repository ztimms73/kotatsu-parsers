# Kotatsu parsers

Library that provides manga sources.

[![](https://jitpack.io/v/KotatsuApp/kotatsu-parsers.svg)](https://jitpack.io/#KotatsuApp/kotatsu-parsers) ![Kotlin](https://img.shields.io/github/languages/top/KotatsuApp/kotatsu-parsers) ![License](https://img.shields.io/github/license/KotatsuApp/Kotatsu) [![Discord](https://img.shields.io/discord/898363402467045416?color=5865f2&label=discord)](https://discord.gg/NNJ5RgVBC5)

### Usage

1. Add it in your root build.gradle at the end of repositories:

   ```groovy
   allprojects {
	   repositories {
		   ...
		   maven { url 'https://jitpack.io' }
	   }
   }
   ```

2. Add the dependency

   For Java/Kotlin project:
    ```groovy
    dependencies {
        implementation("com.github.KotatsuApp:kotatsu-parsers:$parsers_version")
    }
    ```

   For Android project:
    ```groovy
    dependencies {
        implementation("com.github.KotatsuApp:kotatsu-parsers:$parsers_version") {
            exclude group: 'org.json', module: 'json'
        }
    }
    ```

   See for versions at [JitPack](https://jitpack.io/#KotatsuApp/kotatsu-parsers)

3. Usage in code

   ```kotlin
   val parser = MangaSource.MANGADEX.newParser(mangaLoaderContext)
   ```

   `mangaLoaderContext` is an implementation of the `MangaLoaderContext` class.
   See [Android](https://github.com/KotatsuApp/Kotatsu/blob/devel/app/src/main/java/org/koitharu/kotatsu/core/parser/MangaLoaderContextImpl.kt)
   and [Non-Android](https://github.com/KotatsuApp/kotatsu-dl/blob/master/src/main/kotlin/org/koitharu/kotatsu_dl/env/MangaLoaderContextImpl.kt)
   implementation examples.

   Note that the `MangaSource.LOCAL` and `MangaSource.DUMMY` parsers cannot be instantiated.