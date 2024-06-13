package com.rashstudios.animehub

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        Row {
            TitleText(text = "HomePage")
            AnimeSearchField {
                if (it.isNotEmpty()) {
                    navController.navigate("search/${it.replace(' ', '+')}")
                }
            }
        }
        TitleText(text = "Recently Watched")
        RecentlyPlayedShows(navController)
    }
}

@Composable
fun SearchResultsPage(navController: NavController, searchQuery: String) {
    var activeShows by remember { mutableStateOf(listOf<Show>()) }
    var fetching by remember { mutableStateOf(true) }
    LaunchedEffect(true) {
        activeShows = getShows(searchQuery)
        fetching = false
    }
    Text("Searching for ${searchQuery}...")
    if (fetching) {
        CircularProgressIndicator()
    } else {
        TileShows(activeShows, navController)
    }
}


@Composable
fun AnimeSearchField(onSubmit: (String) -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf(TextFieldValue("")) }

    TextField(
        value = searchText,
        placeholder = {
            Text("Search Anime")
        },
        onValueChange = { searchText = it },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController?.hide()
            onSubmit(searchText.text)
        })

    )
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
        val show = RecentlyWatchedShow(
            id, name, thumbnail, episode, timestamp.toLong(), 0
        )
        recentShows.add(show)
    }

    recentShows.sortBy { it.timestamp }

    LaunchedEffect(Unit) {
        delay(1000)
        if (recentShows.size > 0) {
            focusRequester.requestFocus()
        }
    }

    TvLazyRow(modifier = Modifier.padding(16.dp)) {
        items(recentShows.size) {
            val show = recentShows[it]
            AnimeRecentShowCard(
                show = show, navController = navController,
                if (it == 0) focusRequester else null
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
    val position: Long
)


@Composable
fun AnimeRecentShowCard(
    show: RecentlyWatchedShow,
    navController: NavController,
    focusRequester: FocusRequester? = null
) {

    fun onClick() {
        val navigateUrl = "episodes/${
            encodeUriComponent(
                show.id
            )
        }/${encodeUriComponent(show.name)}/${encodeUriComponent(show.thumbnail)}"
        navController.navigate(
            navigateUrl
        )
    }

    var modifier = Modifier
        .padding(4.dp)
        .clickable(onClick = ::onClick)

    if (focusRequester != null) {
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
    }
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(4.dp)) {
            AsyncImage(
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
                model = show.thumbnail,
                contentDescription = "Thumbnail of ${show.name}"
            )
            Text(
                text = show.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text("EPISODE: ${show.episodeNumber}")
        }
    }
}

@Composable
fun TitleText(text: String) {
    Text(modifier = Modifier.padding(4.dp),
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    )
}
