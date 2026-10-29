package cl.androiddlna

/** Pick the show folder rather than a server navigation category or season. */
internal fun shortcutProgramName(titles: List<String>): String? {
    val categories = setOf("biblioteca", "root", "video", "videos", "music", "photos",
        "by folder", "by folders", "por carpeta", "por carpetas", "folders", "carpetas",
        "all videos", "todos los videos", "tv shows", "tv series", "series", "television",
        "movies", "películas", "by title", "por título", "all", "todos",
        "all episodes", "episodes", "todos los episodios", "episodios",
        "all seasons", "seasons", "todas las temporadas", "temporadas")
    val season = Regex("(?i)(season|temporada|temp|s|t)[ ._-]*\\d+.*|specials|especiales")
    return titles.lastOrNull { title ->
        title.isNotBlank() && title.trim().lowercase(java.util.Locale.ROOT) !in categories &&
            !season.matches(title.trim())
    }
}
