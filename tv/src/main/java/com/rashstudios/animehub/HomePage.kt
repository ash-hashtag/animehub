package com.rashstudios.animehub

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.leanback.widget.TitleView
import androidx.navigation.NavController
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.rashstudios.animehub.soap.RecentlyWatchedMoviesComponent
import com.rashstudios.animehub.soap.ThumbnailCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Paths

@Composable
fun HomePage(navController: NavController) {
    Column {
        SearchTextField(onSubmit = {
            if (it.isNotEmpty()) {
                navController.navigate("search/${it.replace(' ', '+')}")
            }
        }, placeHolderText = "Search Anime")
        TitleText(text = "Recently Watched")
        RecentlyPlayedShows(navController)
    }
}

@Composable
fun SearchResultsPage(navController: NavController, searchQuery: String) {
    var activeShows by rememberSaveable(stateSaver = ShowSaver) { mutableStateOf(listOf<Show>()) }
    val snackbarHostState = LocalSnackbarHostState.current
    LaunchedEffect(true) {
        snackbarHostState.showSnackbar("Searching for ${searchQuery}")
        activeShows = getShows(searchQuery)
        snackbarHostState.showSnackbar("Found ${activeShows.size} results")
    }

    TileShows(activeShows, navController)
}

@Composable
fun RootHomePage(navController: NavController) {
    var tabIndex by rememberSaveable {
        mutableStateOf(0)
    }
    val tabs = listOf(
        "Anime",
        "Movies"
    )
    Column {

        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }) {
                    TitleText(tab)
                }
            }
        }

        when (tabIndex) {
            0 -> HomePage(navController)
            1 -> RecentlyWatchedMoviesComponent(navController)
        }
    }
}

@Composable
fun RecentlyPlayedShows(navController: NavController) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val preferences = context.getSharedPreferences("recentlyWatched", Context.MODE_PRIVATE)
    val recentShows = mutableListOf<RecentlyWatchedShow>()
    preferences.all.map {
        val id = it.key
        val value = JSONObject(it.value as String)
        val name = value.getString("name")
        val thumbnail = value.getString("thumbnail")
        val episode = value.getString("episode")
        val timestamp = value.getString("timestamp")
        val mode = value.optString("mode", "sub")
        val position = value.optLong("position", 0)
        val show = RecentlyWatchedShow(
            id, name, thumbnail, episode, timestamp.toLong(), position, mode
        )
        recentShows.add(show)
    }

    recentShows.sortBy { -it.timestamp }

    if (recentShows.size > 20) {
        val editing = preferences.edit()
        for (show in recentShows.reversed()) {
            editing.remove(show.id)
        }
        editing.apply()
    }


    LaunchedEffect(Unit) {
        delay(200)
        if (recentShows.size > 0) {
            focusRequester.requestFocus()
        }
    }


//    TvLazyRow(modifier = Modifier.padding(16.dp)) {
    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.padding(16.dp)) {
        items(recentShows.size) {
            val show = recentShows[it]
            var modifier = Modifier
                .padding(4.dp)
                .width(250.dp)
                .clickable {
                    val navigateUrl = "episodes/${
                        encodeUriComponent(
                            show.id
                        )
                    }/${encodeUriComponent(show.name)}/${encodeUriComponent(show.thumbnail)}"
                    navController.navigate(
                        navigateUrl
                    )
                    println("Navigating ${navigateUrl}")
                }
            if (it == 0) {
                modifier = modifier.focusRequester(focusRequester)
            }
            modifier = modifier.focusable()

            ThumbnailCard(
                thumbnail = show.thumbnail, title = show.name, subtitle =
                "${show.episodeNumber} ${show.mode}", modifier = modifier
            )
        }
    }

}


data class RecentlyWatchedShow(
    val id: String,
    val name: String,
    val thumbnail: String,
    val episodeNumber: String,
    val timestamp: Long,
    val position: Long,
    val mode: String,
)


@Composable
fun TitleText(text: String) {
    Text(
        modifier = Modifier.padding(4.dp),
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    )
}
