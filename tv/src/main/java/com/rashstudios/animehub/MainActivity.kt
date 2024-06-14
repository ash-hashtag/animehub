package com.rashstudios.animehub

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackSeekDataProvider
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.rashstudios.animehub.soap.DEFAULT_THUMBNAIL
import com.rashstudios.animehub.soap.Movie
import com.rashstudios.animehub.soap.SearchResultsMoviesPage
import com.rashstudios.animehub.soap.ThumbnailCard
import com.rashstudios.animehub.soap.TvShowEpisodesGrid
import com.rashstudios.animehub.ui.theme.AnimeHubTheme
import fuel.Fuel
import fuel.HttpResponse
import fuel.method
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.ArrayList
import kotlin.math.max


class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), shape = RectangleShape
                ) {
                    MyApp()
                }
            }
        }
    }
}


suspend fun makeRequest(url: String): HttpResponse {
//    val fuel = FuelBuilder().build()
    val headers = mapOf(
        "referer" to ALLANIME_REFR
    )
//    val request = Request.Builder().url(url).headers(headers).build()
    val response = Fuel.method(url, headers = headers, method = "GET")
    return response
}


suspend fun getShows(query: String): List<Show> {
    val shows = mutableListOf<Show>()
    try {
        val url = searchAnimeUrl(query)
        val response = makeRequest(url)
        val result = JSONObject(response.body)
        val data = result.getJSONObject("data")
        val showsJson = data.getJSONObject("shows")
        val edges = showsJson.getJSONArray("edges")
        for (i in 0 until edges.length()) {
            try {
                val edge = edges.getJSONObject(i)
                val availableEpisodes = edge.getJSONObject("availableEpisodes")
                val show = Show(
                    id = edge.getString("_id"),
                    name = edge.getString("name"),
                    thumbnail = edge.getString("thumbnail"),
                    availableEpisodes = AvailableEpisodes(
                        sub = availableEpisodes.optInt("sub", 0),
                        dub = availableEpisodes.optInt("dub", 0),
                        raw = availableEpisodes.optInt("raw", 0),
                    )
                )
                shows.add(show)
            } catch (e: Exception) {
                println("EXCEPTION ${e.message}")
            }
        }
    } catch (e: Exception) {
        println("EXCEPTTON: ${e.message}")
    }
    return shows
}

const val ALLANIME_REFR = "https://allanime.to"
const val ALLANIME_BASE = "allanime.day"
const val ALLANIME_API = "https://api.${ALLANIME_BASE}"
const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0"

fun searchAnimeUrl(searchAnimeQuery: String, mode: String = "sub"): String {
    val animeQuery = searchAnimeQuery.trim().replace(' ', '+')
    val searchGqlQuery =
        "query(        \$search: SearchInput        \$limit: Int        \$page: Int        \$translationType: VaildTranslationTypeEnumType        \$countryOrigin: VaildCountryOriginEnumType    ) {    shows(        search: \$search        limit: \$limit        page: \$page        translationType: \$translationType        countryOrigin: \$countryOrigin    ) {        edges {            _id name availableEpisodes thumbnail __typename}    }}"

    val variables_query_val =
        "{\"search\":{\"allowAdult\":false,\"allowUnknown\":false,\"query\":\"${animeQuery}\"},\"limit\":40,\"page\":1,\"translationType\":\"${mode}\",\"countryOrigin\":\"ALL\"}"
    val variables = encodeUriComponent(variables_query_val)
    val query = encodeUriComponent(searchGqlQuery)
    val url = "${ALLANIME_API}/api?variables=${variables}&query=${query}"
    return url
}


@Composable
fun SearchTextField(onSubmit: (String) -> Unit, placeHolderText: String = "Search ") {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth(),
        placeholder = {
            Text(placeHolderText)
        },
        value = searchText,
        onValueChange = {
            searchText = it;
        },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController?.hide()
            onSubmit(searchText.text)
        })
    )
}

@Composable
fun AnimeEpisodeListPage(
    navController: NavController, showId: String, showName: String, thumbnail: String
) {
    var availableEpisodesDetails by rememberSaveable(stateSaver = AvailableEpisodesDetailsSaver) {
        mutableStateOf(
            AvailableEpisodesDetails(
                listOf(),
                listOf(),
                listOf(),
            )
        )
    }

    var searchText by remember { mutableStateOf(TextFieldValue("")) }

    var tabIndex by rememberSaveable {
        mutableStateOf(0)
    }

    var filteredEpisodeDetails by rememberSaveable(stateSaver = AvailableEpisodesDetailsSaver) {
        mutableStateOf(
            AvailableEpisodesDetails(
                listOf(),
                listOf(),
                listOf(),
            )
        )
    }

    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(true) {
        snackbarHostState.showSnackbar("Searching for episodes of ${showName}")
        availableEpisodesDetails = getEpisodesList(showId)
        filteredEpisodeDetails = availableEpisodesDetails
        snackbarHostState.showSnackbar("Found Results: SUB: ${availableEpisodesDetails.sub.size}, DUB: ${availableEpisodesDetails.dub.size}, RAW: ${availableEpisodesDetails.raw.size}")
    }


    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
//    val lastWatched = getRecentlyWatched(context, showId)


    Column(modifier = Modifier.padding(16.dp)) {
        Row {
            AsyncImage(model = thumbnail, contentDescription = "Thumbnail of ${showName}")
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = showName, style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.em,
                    )
                )
                TextField(
                    modifier = Modifier.padding(4.dp).fillMaxWidth(),
                    placeholder = {
                        Text("Search Episode")
                    },
                    value = searchText,
                    onValueChange = {
                        searchText = it;

                        val sub =
                            availableEpisodesDetails.sub.filter { ep -> ep.startsWith(searchText.text) }
                        val dub =
                            availableEpisodesDetails.dub.filter { ep -> ep.startsWith(searchText.text) }
                        val raw =
                            availableEpisodesDetails.raw.filter { ep -> ep.startsWith(searchText.text) }

                        filteredEpisodeDetails = AvailableEpisodesDetails(sub, dub, raw)

                    },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        if (searchText.text.isNotEmpty()) {
                            if (availableEpisodesDetails.sub.contains(searchText.text)) {
                                val episodeNumber = searchText.text

                                updateRecentlyWatched(
                                    context,
                                    showId,
                                    showName,
                                    thumbnail,
                                    availableEpisodesDetails.sub,
                                    episodeNumber,
                                    null
                                )
                                val options = MyVideoOptions(
                                    showId,
                                    showName,
                                    availableEpisodesDetails.sub,
                                    episodeNumber
                                )
                                openVideoPlayPage(
                                    context,
                                    options
                                )
                            }
                        }
                    })
                )
            }
        }

        val tabs = listOf("Sub", "Dub", "Raw")

        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, text ->
                Tab(selected = index == tabIndex, onClick = { tabIndex = index }) {
                    Text(text)
                }
            }
        }

        var previoulyWatched: String? = null
        when (tabIndex) {
            0 -> EpisodesGrid(
                showId, showName, thumbnail, filteredEpisodeDetails.sub,
                previoulyWatched, "sub"
            )

            1 -> EpisodesGrid(
                showId, showName, thumbnail, filteredEpisodeDetails.dub,
                previoulyWatched, "dub"
            )

            2 -> EpisodesGrid(
                showId, showName, thumbnail, filteredEpisodeDetails.raw,
                previoulyWatched, "raw"
            )
        }

    }

}


@Composable
fun TileShows(shows: List<Show>, navController: NavController) {
    LazyVerticalGrid(columns = GridCells.Fixed(5)) {
        items(shows.size) {
            val show = shows[it]
            val modifier = Modifier
                .padding(4.dp)
                .clickable {
                    val navigateUrl = "episodes/${
                        encodeUriComponent(
                            show.id
                        )
                    }/${encodeUriComponent(show.name)}/${encodeUriComponent(show.thumbnail)}"
                    navController.navigate(
                        navigateUrl
                    )
                    println("Navigating '${show.name}' $navigateUrl")
                }
                .focusable()
            ThumbnailCard(
                thumbnail = show.thumbnail, title = show.name,
                subtitle = "SUB: ${show.availableEpisodes.dub} DUB: ${show.availableEpisodes.sub} RAW: ${show.availableEpisodes.raw}",
                modifier = modifier
            )
        }
    }
}


@Composable
fun EpisodesGrid(
    showId: String, showName: String, thumbnail: String, episodeNumbers: List<String>,
    previouslyWatched: String? = null, mode: String = "sub"
) {

    val lazyGridState = rememberLazyGridState()

    val focusRequester = remember { FocusRequester() }

    //FIXME: Make it consistent to only focus first time
    // LaunchedEffect(true) {
    //     if (previouslyWatched != null) {
    //         delay(200)
    //         val index = episodeNumbers.indexOf(previouslyWatched)
    //         if (index != -1) {
    //             lazyGridState.animateScrollToItem(index)
    //         }
    //     }
    // }

    val context = LocalContext.current

    fun onClick(episodeNumber: String) {
        println("Updated Viewing Episode Number ${episodeNumber} ${mode}")

        updateRecentlyWatched(
            context, showId,
            showName, thumbnail, episodeNumbers, episodeNumber, null, mode
        )

        openVideoPlayPage(
            context,
            MyVideoOptions(showId, showName, episodeNumbers, episodeNumber, mode)
        )
    }
    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(10), contentPadding = PaddingValues(8.dp)
    ) {
        items(episodeNumbers.size) { index ->
            val episodeNumber = episodeNumbers[index]
            val focused = if (previouslyWatched != null) {
                previouslyWatched == episodeNumber
            } else {
                index == 0
            }
            EpisodeButton(
                episodeNumber, ::onClick, if (focused) {
                    focusRequester
                } else {
                    null
                }
            )
        }
    }
}

@Composable
fun EpisodeButton(
    episodeNumber: String,
    onClick: (String) -> Unit,
    focusRequester: FocusRequester?,
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    LaunchedEffect(Unit) {
        if (focusRequester != null) {
            println("${episodeNumber} HAS FOCUS waiting 1 second before requesting focus")
            delay(1000)
            focusRequester.requestFocus()
        }
    }
    var modifier = Modifier
        .clickable { onClick(episodeNumber) }
        .padding(16.dp)

    if (focusRequester != null) {
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { println("Focus Changed ${episodeNumber} ${it}") }
    }
    Column(
        modifier = modifier
    ) {

        Text(text = episodeNumber)
    }


}

fun updateRecentlyWatched(
    context: Context,
    showId: String,
    showName: String? = null,
    thumbnail: String? = null,
    episodes: List<String>? = null,
    episodeNumber: String? = null,
    position: Long? = null,
    mode: String? = null,
) {
    val pref = context.getSharedPreferences("recentlyWatched", Context.MODE_PRIVATE)
    val o = JSONObject(pref.getString(showId, "{}")!!)
    if (showName != null)
        o.put("name", showName)
    if (thumbnail != null)
        o.put("thumbnail", thumbnail)
    if (episodes != null)
        o.put("episodes", episodes)
    if (episodeNumber != null)
        o.put("episode", episodeNumber)
    if (position != null)
        o.put("position", position)
    if (mode != null)
        o.put("mode", mode)
    o.put("timestamp", System.currentTimeMillis())
    val s = o.toString()
    println("Updated Recently Watched ${showId}: ${s}")
    pref.edit().putString(showId, s).apply()
}

fun getRecentlyWatched(context: Context, showId: String): JSONObject {
    val pref = context.getSharedPreferences("recentlyWatched", Context.MODE_PRIVATE)
    return JSONObject(pref.getString(showId, "{}")!!)
}

fun getEpisodeListUrl(showId: String): String {
    val episodes_list_gql =
        "query (\$showId: String!) {    show(        _id: \$showId    ) {        _id availableEpisodesDetail   }}"
    val variables_query = "{\"showId\":\"${showId}\"}"
    return "${ALLANIME_API}/api?variables=${
        encodeUriComponent(
            variables_query
        )
    }&query=${encodeUriComponent(episodes_list_gql)}"
}

suspend fun getEpisodesList(showId: String): AvailableEpisodesDetails {
    val url = getEpisodeListUrl(showId)
    val response = makeRequest(url)
    val sub = mutableListOf<String>()
    val dub = mutableListOf<String>()
    val raw = mutableListOf<String>()
    try {
        println(response.body)
        val data = JSONObject(response.body)
        val availableEpisodesDetailsJson = data.getJSONObject("data").getJSONObject("show")
            .getJSONObject("availableEpisodesDetail")

        val subEpisodes = availableEpisodesDetailsJson.optJSONArray("sub")
        if (subEpisodes != null) {
            for (i in 0 until subEpisodes.length()) {
                sub.add(subEpisodes.getString(i))
            }
        }
        val dubEpisodes = availableEpisodesDetailsJson.optJSONArray("dub")

        if (dubEpisodes != null) {
            for (i in 0 until dubEpisodes.length()) {
                dub.add(dubEpisodes.getString(i))
            }
        }

        val rawEpisodes = availableEpisodesDetailsJson.optJSONArray("raw")

        if (rawEpisodes != null) {
            for (i in 0 until rawEpisodes.length()) {
                raw.add(rawEpisodes.getString(i))
            }
        }

    } catch (e: Exception) {
        println("EXCEPTION: ${e.message}")
    }
    val availableEpisodesDetails = AvailableEpisodesDetails(sub, dub, raw)
    return availableEpisodesDetails
}

fun getEpisodeUrlUrl(showId: String, episodeNumber: String, mode: String = "sub"): String {
    val episode_embed_gql =
        "query (\$showId: String!, \$translationType: VaildTranslationTypeEnumType!, \$episodeString: String!) {    episode(        showId: \$showId        translationType: \$translationType        episodeString: \$episodeString    ) {        episodeString sourceUrls    }}"
    val variables = URLEncoder.encode(
        "{\"showId\":\"$showId\",\"translationType\":\"$mode\",\"episodeString\":\"$episodeNumber\"}",
        "UTF-8"
    )
    val query = URLEncoder.encode(episode_embed_gql, "UTF-8")
    return "${ALLANIME_API}/api?variables=${variables}&query=${query}"
}

suspend fun getEpisodeUrl(
    showId: String,
    episodeNumber: String,
    mode: String = "sub",
    preferHLS: Boolean = true
): String? {
    try {
        val response = makeRequest(getEpisodeUrlUrl(showId, episodeNumber, mode))
        val body = response.body
        println(body)
        val dataJson = JSONObject(body).getJSONObject("data")
        val episodeJson = dataJson.getJSONObject("episode")
        val sourceUrls = episodeJson.getJSONArray("sourceUrls")

        val providers = mutableListOf<Provider>()

        for (i in 0 until sourceUrls.length()) {
            val sourceJSON = sourceUrls.getJSONObject(i)
            val sourceUrl = sourceJSON.getString("sourceUrl")
            val sourceName = sourceJSON.getString("sourceName")
            val source = Source(sourceName = sourceName, sourceUrl = sourceUrl)
            val provider = getProviderFromSource(source)
            if (provider != null) {
                providers.add(provider)
                val urls = getVideoLinksFromProvider(provider)
                if (preferHLS) {
                    urls.filter { it.endsWith(".m3u8") }
                }
                if (urls.isNotEmpty()) {
                    return urls.first()
                }
            }
        }
    } catch (e: Exception) {
        println("EXCEPTION: ${e.message}")
    }
    return null

}

data class Provider(val providerName: String, val providerId: String)

fun getProviderFromSource(source: Source): Provider? {
    val providerNames = mapOf(
        "Luf-mp4" to "gogoanime",
        "S-mp4" to "sharepoint",
        "Kir" to "wetransfer",
        "Sak" to "dropbox",
    )
    val providerName = providerNames.get(source.sourceName)
    if (providerName == null) {
        println("Couldn't find Provider of ${source.sourceName}")
        return null
    }
    val finalOutput = StringBuilder()
    val patterns = mapOf(
        "01" to '9',
        "08" to '0',
        "05" to '=',
        "0a" to '2',
        "0b" to '3',
        "0c" to '4',
        "07" to '?',
        "00" to '8',
        "5c" to 'd',
        "0f" to '7',
        "5e" to 'f',
        "17" to '/',
        "54" to 'l',
        "09" to '1',
        "48" to 'p',
        "4f" to 'w',
        "0e" to '6',
        "5b" to 'c',
        "5d" to 'e',
        "0d" to '5',
        "53" to 'k',
        "1e" to '&',
        "5a" to 'b',
        "59" to 'a',
        "4a" to 'r',
        "4c" to 't',
        "4e" to 'v',
        "57" to 'o',
        "51" to 'i'
    )
    println("Source URL ${source.sourceUrl}")
    val sourceUrl = source.sourceUrl.replace("--", "")
    for (i in 0..max(0, sourceUrl.length - 1) step 2) {
        val key = sourceUrl.slice(i..i + 1)
        val result = patterns[key]
        if (result == null) {
            println("couldn't find pattern for ${key}")
            return null
        }
        finalOutput.append(result)
    }

    val providerId = finalOutput.toString().replace("clock", "clock.json")
    return Provider(providerName, providerId)
}

suspend fun getVideoLinksFromProvider(provider: Provider): List<String> {
    val url = "https://$ALLANIME_BASE${provider.providerId}"
    println("Getting links from ${url}")
    val response = makeRequest(url)
    val body = response.body
    println(body)
    val data = JSONObject(body)
    val linksJson = data.getJSONArray("links")
    val links = mutableListOf<Link>()
    for (i in 0 until linksJson.length()) {
        val linkJSON = linksJson.getJSONObject(i)
        val link = linkJSON.getString("link")
        val resolutionStr = linkJSON.getString("resolutionStr")
        val hls = linkJSON.optBoolean("hls", false)
        val mp4 = linkJSON.optBoolean("mp4", false)
        links.add(Link(link, resolutionStr, hls, mp4))
    }

    val bestLinks = mutableListOf<String>()
    for (link in links) {
        println("LINK: ${link}")
        val bestQualityLink = getBestQualityLink(link)
        println("BEST QUALITY LINK: ${bestQualityLink}")
        bestLinks.add(bestQualityLink)
    }

    return bestLinks
}

data class Link(val link: String, val resolutionStr: String, val hls: Boolean, val mp4: Boolean)

suspend fun getBestQualityLink(link: Link): String {

    val url = link.link
    if (!(url.contains("vipanicdn") || url.contains("anifastcdn"))) {
        return url
    }

    if (url.contains("original.m3u")) {
        return url
    }

    if (!url.endsWith(".m3u8")) {
        return url
    }

    val resolutions = getM3u8VideoInformation(url).sortedBy { it.height }
    for (res in resolutions) {
        println("RESOLUTION: ${res}")
    }

    if (resolutions.isEmpty()) {
        return url
    } else {
        return resolutions.last().url
    }
}

suspend fun getM3u8VideoInformation(url: String): List<ResolutionAndUrl> {
    val resolutions = mutableListOf<ResolutionAndUrl>()
    val lastIndex = url.lastIndexOf('/')
    if (lastIndex <= 0) {
        return resolutions
    }
    val relativeUrl = url.slice(0..lastIndex)
    println("Getting resolutions form ${url}")
    val response = makeRequest(url)
    val body = response.body
    val lines = body.lines()
    for (i in lines.indices) {
        try {
            val line = lines[i].lowercase()
            val index = line.indexOf("resolution")
            if (index <= 0) {
                continue
            }
            val start = index + "resolution".length
            var end = line.indexOf(',', start) - 1

            if (end <= 0) {
                end = line.length
            }
            val resolutionStr = line.slice(start..end).replace("=", "").trim()
            if (resolutionStr.contains('x')) {
                val splits = resolutionStr.split('x')
                val width = splits[0].toInt()
                val height = splits[1].toInt()
                if (i + 1 < lines.size) {
                    val pathname = lines[i + 1]
                    val exactUrl = "${relativeUrl}${pathname}"
                    val resolution = ResolutionAndUrl(width, height, exactUrl)
                    resolutions.add(resolution)
                }
            }
        } catch (e: Exception) {
            println("EXCEPTION: ${e.message}")
        }
    }
    return resolutions
}

data class ResolutionAndUrl(val width: Int, val height: Int, val url: String)

val ShowSaver = listSaver<List<Show>, List<Any>>(
    save = { list ->
        list.map { it ->
            listOf(
                it.id,
                it.name,
                it.thumbnail,
                it.availableEpisodes.sub,
                it.availableEpisodes.dub,
                it.availableEpisodes.raw
            )
        }
    },
    restore = { list ->
        list.map {
            Show(
                it[0] as String,
                it[1] as String,
                it[2] as String,
                AvailableEpisodes(it[3] as Int, it[4] as Int, it[5] as Int)
            )
        }
    }
)


data class Show(
    val id: String,
    val name: String,
    val thumbnail: String,
    val availableEpisodes: AvailableEpisodes
) {
    fun toJSONObject(): JSONObject {
        val o = JSONObject()
        o.put("_id", id)
        o.put("name", name)
        o.put("thumbnail", thumbnail)
        o.put("availableEpisodes", availableEpisodes.toJSONObject())

        return o
    }

    companion object {
        fun fromJSONObject(o: JSONObject): Show {
            val id = o.getString("_id")
            val name = o.getString("name")
            val thumbnail = o.getString("thumbnail")
            val availableEpisodes =
                AvailableEpisodes.fromJSONObject(o.getJSONObject("availableEpisodes"))

            return Show(
                id, name, thumbnail, availableEpisodes
            )
        }
    }
}

data class AvailableEpisodes(val sub: Int, val dub: Int, val raw: Int) {
    fun toJSONObject(): JSONObject {
        val o = JSONObject()
        o.put("sub", sub)
        o.put("dub", dub)
        o.put("raw", raw)
        return o
    }

    companion object {
        fun fromJSONObject(o: JSONObject): AvailableEpisodes {
            val sub = o.optInt("sub", 0)
            val dub = o.optInt("dub", 0)
            val raw = o.optInt("raw", 0)
            return AvailableEpisodes(sub, dub, raw)
        }
    }
}

val AvailableEpisodesDetailsSaver = listSaver<AvailableEpisodesDetails, Any>(
    save = { listOf(it.sub, it.dub, it.raw) },
    restore = {
        AvailableEpisodesDetails(
            it[0] as List<String>,
            it[1] as List<String>,
            it[2] as List<String>
        )
    }
)

data class AvailableEpisodesDetails(
    val sub: List<String>, val dub: List<String>, val raw: List<String>
)

data class Source(val sourceUrl: String, val sourceName: String)

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MyApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) {

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    RootHomePage(navController)
                }
                composable("error") { ErrorPage() }
                composable(
                    "episodes/{showId}/{showName}/{thumbnail}",
                    arguments = listOf(navArgument("showId") { type = NavType.StringType },
                        navArgument("showName") { type = NavType.StringType },
                        navArgument("thumbnail") { type = NavType.StringType })
                ) {
                    val showId = it.arguments?.getString("showId")
                    val showName = it.arguments?.getString("showName")
                    val thumbnail = it.arguments?.getString("thumbnail")
                    if (showId != null && showName != null) {
                        AnimeEpisodeListPage(
                            navController,
                            decodeUriComponent(showId), decodeUriComponent(showName),
                            if (thumbnail != null)
                                decodeUriComponent(thumbnail) else DEFAULT_THUMBNAIL,
                        )
                    } else {
                        ErrorPage()
                    }
                }
                composable(
                    "search/{query}",
                    arguments = listOf(navArgument("query") { type = NavType.StringType })
                ) {
                    val query = it.arguments?.getString("query")
                    if (query != null)
                        SearchResultsPage(navController, query)
                    else
                        ErrorPage()
                }

                composable(
                    "tvEpisodes/{showUrl}/{showTitle}/{showThumbnail}", arguments =
                    listOf(
                        navArgument("showUrl") { type = NavType.StringType },
                        navArgument("showTitle") { type = NavType.StringType },
                        navArgument("showThumbnail") { type = NavType.StringType },
                    )
                ) {
                    val url = it.arguments?.getString("showUrl")
                    val title = it.arguments?.getString("showTitle")
                    val thumbnail = it.arguments?.getString("showThumbnail")

                    if (url != null && title != null && thumbnail != null) {
                        val movie = Movie(
                            decodeUriComponent(title),
                            decodeUriComponent(url),
                            decodeUriComponent(thumbnail)
                        )
                        TvShowEpisodesGrid(movie = movie)
                    } else {
                        ErrorPage()
                    }
                }

                composable(
                    "searchTv/{query}",
                    arguments = listOf(navArgument("query") { type = NavType.StringType })
                ) {
                    val query = it.arguments?.getString("query")
                    if (query != null) {
                        SearchResultsMoviesPage(navController, decodeUriComponent(query))
                    } else {
                        ErrorPage()
                    }
                }

            }


        }
    }


}

@Composable
fun ErrorPage() {
    Text(text = "Something Strange Happened")
}


fun encodeUriComponent(s: String): String {
    return URLEncoder.encode(s, "UTF-8")
}

fun decodeUriComponent(s: String): String {
    return URLDecoder.decode(s, "UTF-8")
}


data class MyVideoOptions(
    val showId: String,
    val showName: String,
    val episodes: List<String>,
    val episodeNumber: String,
    val mode: String = "sub"
)

class PlaybackActivity() : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val showId = intent.getStringExtra("showId")!!
        val showName = intent.getStringExtra("showName")!!
        val episodes = intent.getStringArrayListExtra("episodes")!!
        val episodeNumber = intent.getStringExtra("episodeNumber")!!
        val mode = intent.getStringExtra("mode")!!

        val myVideoOptions = MyVideoOptions(
            showId,
            showName,
            episodes,
            episodeNumber,
            mode,
        )
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    android.R.id.content,
                    MyVideoFragment(myVideoOptions)
                ).commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}


fun openVideoPlayPage(context: Context, mOptions: MyVideoOptions) {
    val intent = Intent(context, PlaybackActivity::class.java)
    intent.putStringArrayListExtra("episodes", ArrayList(mOptions.episodes))
    intent.putExtra("episodeNumber", mOptions.episodeNumber)
    intent.putExtra("showId", mOptions.showId)
    intent.putExtra("showName", mOptions.showName)
    intent.putExtra("mode", mOptions.mode)

    context.startActivity(intent)
}

val dispatcher = Dispatchers.Main

class MyVideoFragment(var mOptions: MyVideoOptions) : VideoSupportFragment() {

    var mPlayerGlue: VideoPlayerGlue<ExoPlayerAdapter>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundType = BG_LIGHT;
        initPlayerGlue(true)
        playEpisode(mOptions.episodeNumber)
    }

    fun initPlayerGlue(isHls: Boolean) {
        val playerAdapter = ExoPlayerAdapter(requireContext(), isHls)
        val playerGlue = VideoPlayerGlue(
            requireContext(), playerAdapter, mOptions,
            ::playNextVideo, ::playPreviousVideo
        )
        playerGlue.setHost(VideoSupportFragmentGlueHost(this))
        playerGlue.addPlayerCallback(object : PlaybackGlue.PlayerCallback() {
            override fun onPreparedStateChanged(glue: PlaybackGlue) {
                if (glue.isPrepared()) {
                    playerGlue.seekProvider = PlaybackSeekDataProvider()
                    playerGlue.play()
                    println("Playing Video ${glue}")
                }
            }
        })
        mPlayerGlue = playerGlue
    }


    fun playEpisode(episodeNumber: String, position: Long = 0) {
        mOptions = mOptions.copy(episodeNumber = episodeNumber)

        CoroutineScope(dispatcher).launch {
            val url = getEpisodeUrl(mOptions.showId, mOptions.episodeNumber, mOptions.mode)
            if (url != null) {
                val playerGlue = mPlayerGlue
                if (playerGlue != null) {
                    playerGlue.playerAdapter.reset()
                    playerGlue.title = mOptions.showName
                    playerGlue.subtitle = "EPISODE #${mOptions.episodeNumber}"
                    playerGlue.playerAdapter.setDataSource(Uri.parse(url))
                }
            } else {
                println("EXCEPTION: URL IS NULL")
            }
        }
    }

    fun playNextVideo(options: MyVideoOptions) {
        val index = options.episodes.indexOf(options.episodeNumber)
        val nextEpisodeNumber = options.episodes.getOrNull(index - 1)
        if (nextEpisodeNumber != null)
            playEpisode(nextEpisodeNumber)
    }

    fun playPreviousVideo(options: MyVideoOptions) {
        val index = options.episodes.indexOf(options.episodeNumber)
        val previousEpisodeNumber = options.episodes.getOrNull(index + 1)
        if (previousEpisodeNumber != null)
            playEpisode(previousEpisodeNumber)
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentPosition = mPlayerGlue?.currentPosition
        updateRecentlyWatched(
            requireContext(), mOptions.showId,
            null,
            null,
            null,
            mOptions.episodeNumber,
            currentPosition,
            mOptions.mode,
        )
        mPlayerGlue?.playerAdapter?.release()
    }
}

fun convertMillisToHMS(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
}




