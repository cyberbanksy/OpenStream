package com.orgista.openstream.data

/**
 * Public catalog providers requested for OpenStream. Personal/debrid provider
 * URLs are deliberately not compiled into the APK; viewers add those through
 * Sources, where they remain in app-private storage.
 */
object BuiltInStremioAddons {
    private fun addon(
        manifestId: String,
        name: String,
        manifestUrl: String,
        version: String = "built-in",
        resources: Set<String>,
        catalogs: List<StremioCatalog>,
    ) = StremioAddon(
        id = "builtin:$manifestId",
        manifestId = manifestId,
        name = name,
        transportUrl = manifestUrl.removeSuffix("/manifest.json"),
        version = version,
        resources = resources,
        catalogs = catalogs,
        builtIn = true,
    )

    val all: List<StremioAddon> = listOf(
        addon(
            manifestId = "com.linvo.cinemeta",
            name = "Cinemeta",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            resources = setOf("catalog", "meta", "addon_catalog"),
            catalogs = listOf(
                StremioCatalog("movie", "top", "Popular movies", supportsSearch = true),
                StremioCatalog("series", "top", "Popular series", supportsSearch = true),
                StremioCatalog("movie", "imdbRating", "Featured movies"),
                StremioCatalog("series", "imdbRating", "Featured series"),
            ),
        ),
        addon(
            manifestId = "pw.ers.netflix-catalog",
            name = "Streaming Catalogs",
            manifestUrl = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json",
            resources = setOf("catalog"),
            catalogs = listOf(
                StremioCatalog("movie", "nfx", "Netflix movies"),
                StremioCatalog("series", "nfx", "Netflix series"),
            ),
        ),
        addon(
            manifestId = "pw.ers.rottentomatoes",
            name = "Rotten Tomatoes",
            manifestUrl = "https://7a82163c306e-rottentomatoes.baby-beamup.club/%7B%22rpdb_key%22%3A%22t0-free-rpdb%22%7D/manifest.json",
            resources = setOf("catalog"),
            catalogs = listOf(
                StremioCatalog("movie", "rtfresh_movie", "Certified Fresh"),
                StremioCatalog("series", "rtfresh_series", "Fresh TV"),
            ),
        ),
        addon(
            manifestId = "tmdb-addon",
            name = "The Movie Database Addon",
            manifestUrl = "https://tmdb.elfhosted.com/N4IgTgDgJgRg1gUwJ4gFwgC4AYC0AzMBBHSWEAGnGhgFkEoBLAQwBUkIEBnNUCAe04YEYNBjABXBJQA2fAOZ9REqSBhMAxnChg+EJZIC+lOQgC2DAHYNEKdAEEAkgC8mAZSQAhDBYAiACwsMJzkfAAkAMQBxLABFMDgnKAANJwQmACYAGQB2AEcAJTwATQwALTsKEDkdXJs0Ks44AH0sODsAOSKWABZpP0yaOWkARg8AaVKfDwsAdUioJBgAZnCigCkAVUz5DekGJAA1PzsABTG+BgAOAE4T0oA2MHzLyoxTWDsIBjHkeqh7y5Qa7DboAVlB3QQ9yw4Ou6SWeDwSyg2Wu2Uu8Jg9wQS0q0iYFjk4iYJnqCAsOA2rkqnC4nAYfAsDigf2u6mG6W62QQYOu126Sxg3Wx6Twl0uMDw9266iW6no93l6igWHU93uNLEaXMhLQAG0QBY8AAPSp+GCmSpQCx6ShMUy2kB+aTiSoQdSOiAOq0cSrqDDcAC6lHUTAwTFkcm4qD1oAYLPQb1gADp8UJBK92Ah6qY+AA3BjZygWe3Z9CZMNcDAAAnyCGkaVp3EonD8fAA7g4LKE+KYy2JDOQ4wnMO8YKnKxnKBgs-VaWBC83DaX6hX0zW6w2mE2aW3O93e-3lEZh-Uk+OtRZGLrp7P0LmC0Xl336ixCFfLHJdx2uz2X6gBwQE8QHjM8x2TS9ry-W9fXQedF0qEt-xAN9ySg799z-I9B1PRNwIwXRM1gkAH0LRCV3QE5dHEfERBbPdf0PfQgKHECR3PCDCJgssQHgrhyOQqiIBopg6N4hiD3-QDgNAvCUyQNIxJnYjSKfJCeKKRSMMYqTj1Y2TR3krTuLnYQEOLCiQE00TtMk7CWNwwzx3xQliVJEz73zMiLOQitXJJJ9Wx-OzmJk9jwJcokAqIni+KXdTVwJKL3PE4KsNC-SR0EQh7U-ZMjVNDySK8tTLPaBAMDwPZCtSzCmIAvTHOy7U8oKmLTIXfifJ48rKuq2z0oanC2LnLVcsJZNzUtIrVIEnjQg8AB5asaCYGqgrq3ThoM5rxrkSaLXauCzK6595qWla1oG+rpMy0acp1fbrUdZSeNm7r6h8BhOAsZAAGprq2hyRrgsbHuTZ6jt4k74ssr6fv+wH7LC+6Wom+0XrvYrHzm+oTgXPtqwOeMEEUei0puxqQd4sG8oxqG4txyiCYQImSbJ2qdORu7QYevLnVdGaSqZkBQhowXOZCobgZ22mJoFhmYZFsWXSRjKmrl-b3UxlThY+yi0nUPhNGrfGzAYcRpslwbbo1vmJu1xXOthwTDeNuBTcIcxLbV6WUd5tGte9IWcf1kATlE+0+HEQIAfJzbubtwPky9HXYqVsOI7AKOY4wOPrcp7ass1iHfRD7yzs+76jbzYQkHzjaufV6ndvBqAy8wLHGbD+Ga7rhuJJtqnZft-b-SXV6cz1yv0AAYXEBcBAYGdq1cMHfdtluS-Hp3zJnkB58X+kV7Xh6N+PYMQFDQRZ+jwI0FBeO7BMfIw0-LtInJQho0wZRKnJJgMAGzPwQK-DAn56h4AjLSDCICwGfhmMvPwDgxzwN1OgKB0gYEtjEAwf0dY5AMgsOEBg0ghAiAwdAp8jBCHhmkJuRsCASFkOEGgTBtIDBAA/manifest.json",
            resources = setOf("catalog", "meta"),
            catalogs = listOf(
                StremioCatalog("movie", "tmdb.latest", "Latest movies"),
                StremioCatalog("series", "tmdb.latest", "Latest series"),
                StremioCatalog("movie", "tmdb.trending", "Trending movies"),
                StremioCatalog("series", "tmdb.trending", "Trending series"),
            ),
        ),
    )
}
