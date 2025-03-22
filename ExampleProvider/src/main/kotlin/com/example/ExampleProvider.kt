package com.example

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.readValues
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.ui.result.buildResultEpisode
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getExtractorApiFromName
import com.lagradost.cloudstream3.utils.schemaStripRegex
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class ExampleProvider() : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://embed.warezcdn.link"
    override var name = "WarezCdn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = false
    val infoUrl = "https://api.themoviedb.org/3/find/"



    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://warezcdn.link/includes/ajax.php"
        val response = app.post(url, data=mapOf("searchBar" to query.replace(" ", "+")));
        val json = mapper.readValue<MovieResponse>(response.text)
        Log.d("Mytag", json.list.toString());

        val list = json.list.values
        return list.map { movie ->
            newMovieSearchResponse(name = movie.title, url = "${movie.type.replace("movie", "filme")}/${movie.imdb}"){
                posterUrl = "https://warezcdn.link/content/${movie.type}s/posterPt/342/${movie.id}.webp";
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query);
    }

    suspend fun getSourceUrl(id: Int):String{
        var url = "https://embed.warezcdn.link/getEmbed.php?id=${id}&sv=warezcdn";

        app.get(url, referer = url)

        val response = app.get("https://embed.warezcdn.link/getPlay.php?id=${id}&sv=warezcdn", referer=url).text;

        val movie_url = response.split("href = \"")[1].split("\";\n\t")[0].split("video/")[1].split("?sub=")
        val videoID = movie_url[0]; //change later

        val stream = "https://basseqwevewcewcewecwcw.xyz/player/index.php?data=${videoID}&do=getVideo"

        val returned = app.post(stream, data = mapOf("hash" to videoID, "r" to  "https://embed.warezcdn.link/"), headers = mapOf("x-requested-with" to "XMLHttpRequest"))
        val json = JSONObject(returned.text)
        return json.getString("securedLink");
    }

    override suspend fun load(url: String): LoadResponse? {

//        Log.d("Mytag", "hello");

        val movie = url.contains("filme");
        if(movie){
            val response = app.get(url).text
            val sourcesString = response.split("let data = \'")[1].split("\';\n\t\tvar currentTitle")[0]
            val sources = mapper.readValue<List<Media>>(sourcesString);
            val title = response.split("currentTitle = '")[1].split("';\n\n\t\t\$(function()")[0]

//            Log.d("Mytag", sources[0].id.toString());

            return newMovieLoadResponse(name = title, url="", type= TvType.Movie, dataUrl =getSourceUrl(sources[1].id)){
                posterUrl = response.split("image: url('")[1].split("');\"></backdrop>")[0];

            }
        }
        else{
            val response = app.get(url)
            val poster = response.document.select("backdrop").attr("style").split("\'")[1]
            val title = response.text.split(";\n\t\tvar currentTitle = \"")[1].split("\";\n\n\t\t\$(function ()")[0]

            val seasonsUrl = response.text.split("var cachedSeasons = \"")[1].split("\";\n\t\tvar preLoadSeason")[0]
            val seasonsInfo = mapper.readValue<ShowData>(app.get("${mainUrl}/$seasonsUrl)").text).seasons.values.toList()

            val episodes = ArrayList<Episode>();
            seasonsInfo.forEachIndexed{index, season ->
                season.episodes.values.toList().forEachIndexed{i, epi->
                    episodes.add(newEpisode(epi.id){
                        this.name=epi.titlePt?:"Episode ${i+1}"
                        this.season=index+1
                        this.episode = i+1
                        this.posterUrl ="https://warezcdn.link/content/series/episodes/185/${epi.id}.webp"
                        this.rating = epi.rating.toInt()
                        this.runTime = epi.runtime
                    })
                }
            }


            return newTvSeriesLoadResponse(name=title, type=TvType.TvSeries, url="", episodes = episodes){
                posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(ExtractorLink(name, name, data,"", Qualities.P1080.value, INFER_TYPE));
        return true;
        return super.loadLinks(data, isCasting, subtitleCallback, callback)
    }


}