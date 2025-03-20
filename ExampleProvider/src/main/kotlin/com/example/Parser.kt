package com.example


data class MovieResponse(
    val status: String,
    val count: Int,
    val list: Map<String, MovieItem> // The "list" object has a dynamic key structure like '0', '1', '2'...
)

data class MovieItem(
    val type: String,
    val isAnime: Int,
    val imdb: String,
    val id: Int,
    val title: String,
    val year: Int
)


data class Episode(
    val id: String,
    val name: String,
    val editedName: String? = null,
    val released: String,
    val titlePt: String,
    val rating: String,
    val runtime: String? = null,
    val airdate: String
)

data class Season(
    val id: String,
    val name: String,
    val episodesCount: Int,
    val episodes: Map<String, Episode>
)

data class ShowData(
    val seasonCount: Int,
    val seasons: Map<String, Season>
)

data class Media(val id: Int, val audio: String, val servers: String)
