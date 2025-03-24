package com.example

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject

class ExampleProvider() : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://embed.warezcdn.link"
    override var name = "WarezCdn"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    override var lang = "en"
    override val hasMainPage = false
//    val infoUrl = "https://api.themoviedb.org/3/find/"


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

    override suspend fun load(url: String): LoadResponse? {

        Log.d("Mytag", "load");

        val movie = url.contains("filme");
        if(movie){
            val response = app.get(url).text
            val sourcesString = Regex("let data = \'(.*?)\'").find(response)?.groupValues?.get(1)?:""

            val title = Regex("var currentTitle = \'(.*?)\'").find(response)?.groupValues?.get(1)?:""
            val banner = Regex("url\\(\'(.*?)\'").find(response)?.groupValues?.get(1)?:""
            val id = Regex("1280/(.*?).webp").find(banner)?.groupValues?.get(1)?:""

            return newMovieLoadResponse(name = title, url=sourcesString, type= TvType.Movie, dataUrl=sourcesString){
                posterUrl = "https://warezcdn.link/content/movies/posterPt/342/${id}.webp"
                backgroundPosterUrl = banner
            }
        }
        else{
            val response = app.get(url)
            val poster = response.document.select("backdrop").attr("style").split("\'")[1]
            val id = Regex("1280/(.*?).webp").find(poster)?.groupValues?.get(1)?:""

            val title = Regex("var currentTitle = \"(.*?)\";").find(response.text)?.groupValues?.get(1)?:""

            val seasonsUrl = Regex("var cachedSeasons = \"(.*?)\";").find(response.text)?.groupValues?.get(1)?:""
            val seasonsInfo = mapper.readValue<ShowData>(app.get("$mainUrl/$seasonsUrl)").text).seasons.values.toList()

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
                backgroundPosterUrl = poster
                posterUrl = "https://warezcdn.link/content/series/posterPt/342/${id}.webp"
            }
        }
    }

    suspend fun getSourceUrl(id: Int):String{
        var url = "https://embed.warezcdn.link/getEmbed.php?id=${id}&sv=warezcdn";
        app.get(url, referer = url)
        val response = app.get("https://embed.warezcdn.link/getPlay.php?id=${id}&sv=warezcdn", referer=url).text;

        val videoUrl = Regex("href = \"(.*?)\"").find(response)?.groupValues?.get(1)?.split("?sub=")
        val videoID = videoUrl?.get(0)?:""//change later
        val subUrl = videoUrl?.get(1)?:""

        val stream = "https://basseqwevewcewcewecwcw.xyz/player/index.php?data=${videoID}&do=getVideo"

        val returned = app.post(stream, data = mapOf("hash" to videoID, "r" to  "https://embed.warezcdn.link/"), headers = mapOf("x-requested-with" to "XMLHttpRequest"))
        val json = JSONObject(returned.text)
        return json.getString("securedLink");
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Mytag", "loadLinks with data $data")

        var mediaList : List<Media> = listOf();
        if(data.contains("audio")){ //movie
            mediaList = mapper.readValue<List<Media>>(data)
        }
        else{
            val response = app.get("https://embed.warezcdn.link/core/ajax.php?audios=${data.replace("$mainUrl/", "")}", referer = "https://embed.warezcdn.link").text
            val str = mapper.readValue(response, String::class.java)
            mediaList = mapper.readValue<List<Media>>(str)
        }
        mediaList.forEach{
            if(it.servers.contains("mixdrop")){
                val url = "https://embed.warezcdn.link/getEmbed.php?id=${it.id}&sv=mixdrop";

                app.get(url, referer = url)
                val response = app.get("https://embed.warezcdn.link/getPlay.php?id=${it.id}&sv=mixdrop", referer=url).text;
                val maybe = Regex("href = \"(.*?)\"").find(response)?.groupValues?.get(1)?:""

                loadExtractor(maybe, subtitleCallback, callback);

            }
            val lang =  if(it.audio==2) "Dublado" else "Legendado"

            val url = getSourceUrl(it.id)
            callback.invoke(ExtractorLink(source=name,name ="WarezCDN $lang"  , url,"", getQualityFromName(url), INFER_TYPE));

        }



        return true;
    }


}