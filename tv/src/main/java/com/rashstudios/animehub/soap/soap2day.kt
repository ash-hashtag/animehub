package com.rashstudios.animehub.soap

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.media3.extractor.text.webvtt.WebvttCssStyle.FontSizeUnit
import androidx.navigation.NavController
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.rashstudios.animehub.AGENT
import com.rashstudios.animehub.LocalSnackbarHostState
import com.rashstudios.animehub.RecentlyPlayedShows
import com.rashstudios.animehub.RecentlyWatchedShow
import com.rashstudios.animehub.SearchTextField
import com.rashstudios.animehub.TitleText
import com.rashstudios.animehub.dispatcher
import com.rashstudios.animehub.encodeUriComponent
import fuel.Fuel
import fuel.get
import fuel.method
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.Arrangement.Horizontal
import androidx.compose.ui.Alignment

data class Movie(
    val title: String,
    val url: String,
    val thumbnail: String,
)

val MovieSaver = listSaver<List<Movie>, List<String>>(
    save = { list ->
        list.map { listOf(it.title, it.url, it.thumbnail) }
    },
    restore = { list ->
        list.map { Movie(it[0] as String, it[1] as String, it[2] as String) }
    }
)


const val DEFAULT_SOAP_HOST = "https://soaper.live"

//const val DEFAULT_SOAP_HOST = "https://soap2day.repair"
const val DEFAULT_THUMBNAIL = "https://picsum.photos/480/720"

fun getSoapHost(context: Context): String {
    val config = getConfigPreferences(context)
    val host = config.getString("SOAP_HOST", DEFAULT_SOAP_HOST)
    return host!!
}

suspend fun searchMovieAndShows(context: Context, input: String): List<Movie> {
    val movies = mutableListOf<Movie>()
    try {
        val host = getSoapHost(context)
        val key = input.replace(" ", "%20")
        val url = "$host/search/keyword/$key"

        val response = Fuel.get(url)

        val body = response.body

//        println(body)

        val parser = Ksoup.parse(body)
        val elements = parser.select(".thumbnail")


        for (element in elements) {
            println("------------------------")
            println(element.outerHtml())
            println("------------------------")
            val anchors = element.select("a")
            val thumnailEl = element.select("img").first()
            val thumbnail = thumnailEl?.attribute("src")
            for (anchor in anchors) {
                val title = anchor.text()
                val href = anchor.attribute("href")
                if (title.isNotEmpty() && href != null) {
                    val movie = Movie(
                        title,
                        href.value,
                        thumbnail?.value?.replace("soaper.tv", "soaper.live") ?: DEFAULT_THUMBNAIL,
                    )
                    movies.add(movie)
                    break
                }
            }
        }
    } catch (e: Exception) {
        println("EXCEPTION GETTING MOVIES: ${e.message}")
    }

    return movies
}


fun getConfigPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("config", Context.MODE_PRIVATE)
}

data class TvEpisode(
    val title: String,
    val href: String,
)

data class SeasonedTvEpisodes(
    val season: String,
    val episodes: List<TvEpisode>
)

val SeasonedTvEpisodesSaver = listSaver<List<SeasonedTvEpisodes>, List<Any>>(
    save = { list ->
        list.map {
            val flattened = it.episodes.map { episode -> listOf(episode.title, episode.href) }
            listOf(
                it.season,
                flattened,
            )
        }
    },
    restore = { list ->
        list.map {
            val episodes = (it[1] as List<List<String>>).map { el -> TvEpisode(el[0], el[1]) }

            SeasonedTvEpisodes(
                it[0] as String,
                episodes
            )
        }

    }
)


suspend fun getTvEpisodes(context: Context, show: Movie): List<SeasonedTvEpisodes> {
    val seasonedTvEpisodes = mutableListOf<SeasonedTvEpisodes>()
    try {


        val host = getSoapHost(context)
        val resp = Fuel.get("${host}${show.url}")
        val respBody = resp.body
        val parser = Ksoup.parse(respBody)
        println(respBody)
        val containers = parser.select(".alert-info-ex")
        for (i in containers.reversed().indices) {
            val container = containers[i]
            val seasonName = container.selectFirst("h4")?.text() ?: "Season $i"
            val episodes = mutableListOf<TvEpisode>()
            val anchors = container.select("a")
            for (anchor in anchors) {
                val href = anchor.attribute("href")
                if (href != null && href.value.startsWith("/episode_")) {
                    val title = anchor.text()
                    val ep = TvEpisode(title, href.value)
                    episodes.add(ep)
                }
            }
            episodes.reverse()
            seasonedTvEpisodes.add(SeasonedTvEpisodes(seasonName, episodes))
        }
    } catch (e: Exception) {
        println("Exception getting episodes: ${e.message}")
    }

    seasonedTvEpisodes.reverse()
    return seasonedTvEpisodes
}

suspend fun getEpisodeLink(context: Context, episode: TvEpisode, movie: Movie): MovieVideo? {
    try {
        val episodeId = episode.href
            .split('_').last()
            .replace(".html", "")
        val host = getSoapHost(context)

        val headers = mapOf(
            "referer" to "${host}episode_${episodeId}",
            "agent" to AGENT,
            "content-type" to "application/x-www-form-urlencoded"
        )
        val url = "${host}/home/index/GetEInfoAjax"
        val body = "pass=${episodeId}"

        println("Fetching ${url} with body ${body} and headers ${headers}")

        val resp = Fuel.method(url, method = "POST", headers = headers, body = body)
        val respBody = resp.body
        println(respBody)

        val json = JSONObject(respBody)
        val videoUrl = json.getString("val")
        val subs = json.optJSONArray("subs") ?: JSONArray()
        var englishSub = ""
        val vtt = json.optString("vtt", "")
        for (i in 0 until subs.length()) {
            val sub = subs.getJSONObject(i)
            if (sub.optString("name", "") == "en") {
                val path = sub.optString("path", "")
                englishSub = path
                break
            }
        }
        val directUrl = "${DEFAULT_SOAP_HOST}${videoUrl}"
        return MovieVideo(
            url = directUrl,
            sub = if (englishSub.isNotEmpty())
                "${host}${englishSub}"
            else
                null,
            vtt = if (vtt.isNotEmpty()) vtt else null,
            title = movie.title,
            subtitle = episode.title,
        )
    } catch (e: Exception) {
        println("Exception getting episode link ${e.message}")
    }

    return null

}

@Composable
fun TvShowEpisodesGrid(movie: Movie) {
    var seasons by rememberSaveable(stateSaver = SeasonedTvEpisodesSaver) { mutableStateOf(listOf<SeasonedTvEpisodes>()) }
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(true) {
        snackbarHostState.showSnackbar("Searching Episodes...")
        seasons = getTvEpisodes(context, movie)
        val episodesCount = seasons.map { it.episodes.size }.reduceOrNull({ a, b -> a + b }) ?: 0
        snackbarHostState.showSnackbar("Seasons found ${seasons.size}, Episodes found ${episodesCount}")
    }

    Column {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(6.dp)
        ) {
            AsyncImage(
                model = movie.thumbnail,
                contentDescription = "Thumbnail of ${movie.title}",
                modifier = Modifier.padding(4.dp)
            )
            Text(
                text = movie.title,

                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.em,
                )
            )
        }
        LazyColumn {
            items(seasons.size) { seasonIndex ->
                val season = seasons[seasonIndex]
                TitleText(text = season.season)
                NonLazyGrid(columns = 5, itemCount = season.episodes.size) { episodeIndex ->
                    val episode = season.episodes[episodeIndex]
                    Card(modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable {
                            println("Playing ${movie.title} episode #${episode}")
                            CoroutineScope(dispatcher).launch {
                                val video = getEpisodeLink(context, episode, movie)
                                if (video != null) {
                                    updateRecentlyWatchedTvShow(context, movie)
                                    openBasicVideoPlayPage(context, video)
                                } else {
                                    snackbarHostState.showSnackbar("Couldn't get video link")
                                    println("Exception Video link is null")
                                }
                            }
                        }
                        .focusable()) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            TitleText(text = episode.title)
//                            Text(episode.href)
                        }
                    }
                }


            }

        }
    }
}


@Composable
fun NonLazyGrid(
    columns: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable() (Int) -> Unit
) {
    Column(modifier = modifier) {
        var rows = (itemCount / columns)
        if (itemCount.mod(columns) > 0) {
            rows += 1
        }

        for (rowId in 0 until rows) {
            val firstIndex = rowId * columns

            Row {
                for (columnId in 0 until columns) {
                    val index = firstIndex + columnId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (index < itemCount) {
                            content(index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentlyWatchedMoviesComponent(navController: NavController) {
    val context = LocalContext.current
    val movies = getRecentlyWatchedTvShows(context)
    val snackbarHostState = LocalSnackbarHostState.current
    Column {
        SearchTextField(onSubmit = {
            if (it.isNotEmpty()) {
                if (it.startsWith(":>")) {
                    val parts = it.removePrefix(":>").trim().split("=")
                    val key = parts.getOrNull(0)?.trim()
                    val value = parts.getOrNull(1)?.trim()
                    if (key != null && value != null) {
                        getConfigPreferences(context).edit().putString(key, value).apply()
                    }
                } else {
                    val navigateUrl = "searchTv/${encodeUriComponent(it)}"
                    navController.navigate(navigateUrl)
                }
            }
        })
        TitleText(text = "Recently Watched")
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp)) {
            items(movies.size) {
                val movie = movies[it].movie
                ThumbnailCard(thumbnail = movie.thumbnail,
                    title = movie.title,
                    subtitle = if (movie.url.startsWith("/movie_"))
                        "Movie"
                    else if (movie.url.startsWith("/tv_"))
                        "Series"
                    else
                        "Unknown",
                    modifier = Modifier
//                        .width(250.dp)
                        .padding(4.dp)
                        .clickable {
                            if (movie.url.startsWith("/movie_")) {
                                CoroutineScope(dispatcher).launch {
                                    val video = getMovieLink(context, movie)
                                    if (video != null) {
                                        println("MOVIE LINK ${video}")
                                        println(video.url)
                                        updateRecentlyWatchedTvShow(context, movie)
                                        openBasicVideoPlayPage(context, video)
                                    } else {
                                        println("Exception movie video link is null")
                                        snackbarHostState.showSnackbar("Couldn't get Video link")
                                    }
                                }
                            } else if (movie.url.startsWith("/tv_")) {
                                val navigateUrl = "tvEpisodes/${encodeUriComponent(movie.url)}/${
                                    encodeUriComponent(movie.title)
                                }/${encodeUriComponent(movie.thumbnail)}"
                                println("Navigating to ${navigateUrl}")
                                navController.navigate(navigateUrl)
                            }
                        }
                        .focusable())
            }
        }
    }
}

@Composable
fun SearchResultsMoviesPage(navController: NavController, query: String) {
    val context = LocalContext.current
    var movies by rememberSaveable(stateSaver = MovieSaver) { mutableStateOf(listOf<Movie>()) }
    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(true) {
        snackbarHostState.showSnackbar("Searching for ${query}")
        movies = searchMovieAndShows(context, query)
        snackbarHostState.showSnackbar("Found ${movies.size} result")
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.padding(16.dp)) {
        items(movies.size) {
            val movie = movies[it]
            ThumbnailCard(thumbnail = movie.thumbnail,
                title = movie.title,
                subtitle = if (movie.url.startsWith("/movie_"))
                    "Movie"
                else if (movie.url.startsWith("/tv_"))
                    "Series"
                else
                    "Unknown",
                modifier = Modifier
                    .padding(4.dp)
                    .width(250.dp)
                    .clickable {
                        if (movie.url.startsWith("/movie_")) {
                            CoroutineScope(dispatcher).launch {
                                val video = getMovieLink(context, movie)
                                if (video != null) {
                                    println("MOVIE LINK ${video}")
                                    println(video.url)
                                    updateRecentlyWatchedTvShow(context, movie)
                                    openBasicVideoPlayPage(context, video)
                                } else {
                                    println("Exception video link is null")
                                    snackbarHostState.showSnackbar("Couldn't get video link")
                                }
                            }
                        } else if (movie.url.startsWith("/tv_")) {
                            val navigateUrl = "tvEpisodes/${encodeUriComponent(movie.url)}/${
                                encodeUriComponent(movie.title)
                            }/${encodeUriComponent(movie.thumbnail)}"
                            println("Navigating to ${navigateUrl}")
                            navController.navigate(navigateUrl)
                        }
                    }
                    .focusable())
        }
    }

}


@Composable
fun ThumbnailCard(
    thumbnail: String,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(4.dp)) {
            AsyncImage(
                contentScale = ContentScale.Crop,
                model = thumbnail, contentDescription = "Thumbnail of ${title}"
            )
            TitleText(title)
            if (null != subtitle) {
                Text(text = subtitle, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

suspend fun getMovieLink(context: Context, movie: Movie): MovieVideo? {
    try {


        val host = getSoapHost(context)
        val movieId = movie.url
            .split('_').last()
            .replace(".html", "")
        val url = "${host}/home/index/GetMInfoAjax"
        val body = "pass=${movieId}"
        val headers = mapOf(
            "referer" to "${host}movie_${movieId}",
            "agent" to AGENT,
            "content-type" to "application/x-www-form-urlencoded"
        )
        println("Fetching ${url} with body ${body} and headers ${headers}")
        val resp = Fuel.method(url, headers = headers, method = "POST", body = body)
        val respBody = resp.body

        val json = JSONObject(respBody)
        val videoUrl = json.getString("val")
        val subs = json.optJSONArray("subs") ?: JSONArray()
        var englishSub = ""
        val vtt = json.optString("vtt", "")
        for (i in 0 until subs.length()) {
            val sub = subs.getJSONObject(i)
            if (sub.optString("name", "") == "en") {
                val path = sub.optString("path", "")
                englishSub = path
                break
            }
        }
        val directUrl = "${host}${videoUrl}"
        return MovieVideo(
            url = directUrl,
            sub = if (englishSub.isNotEmpty())
                "${host}${englishSub}"
            else
                null,
            vtt = vtt.ifEmpty { null },
            title = movie.title,
            subtitle = directUrl
        )
    } catch (e: Exception) {
        println("Exception getting movie link ${e.message}")
    }
    return null
}

data class MovieVideo(
    val url: String,
    val sub: String?,
    val vtt: String?,
    val title: String? = null,
    val subtitle: String? = null,
)

data class RecentlyWatchedMovie(
    val movie: Movie,
    val timestamp: Long,
)

fun getRecentlyWatchedTvShows(context: Context): List<RecentlyWatchedMovie> {
    val prefs = recentlyWatchedTvShowsPrefs(context)
    val shows = prefs.all.map {
        val url = it.key
        val o = JSONObject(it.value as String)
        val thumbnail = o.getString("thumbnail")
        val title = o.getString("title")
        val movie = Movie(title, url, thumbnail)
        val timestamp = o.getLong("timestamp")
        RecentlyWatchedMovie(movie, timestamp)
    }

    shows.sortedBy { -it.timestamp }

    if (shows.size > 20) {
        val editing = prefs.edit()
        for (show in shows.reversed()) {
            editing.remove(show.movie.url)
        }
        editing.apply()
    }
    return shows
}

fun updateRecentlyWatchedTvShow(context: Context, movie: Movie) {
    var editing = recentlyWatchedTvShowsPrefs(context).edit()
    val o = JSONObject()
    o.put("title", movie.title)
    o.put("thumbnail", movie.thumbnail)
    o.put("timestamp", System.currentTimeMillis())
    editing.putString(movie.url, o.toString())
    editing.apply()
}

fun recentlyWatchedTvShowsPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences("recentTvShows", Context.MODE_PRIVATE)
}
