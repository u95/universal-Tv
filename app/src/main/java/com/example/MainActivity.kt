package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class Channel(val name: String, val language: String, val category: String, val streamUrl: String, val logoUrl: String = "")


class ChannelViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        fetchChannels()
    }

    fun fetchChannels() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val fetchedChannels = withContext(Dispatchers.IO) {
                    val tamM3u = try { URL("https://iptv-org.github.io/iptv/languages/tam.m3u").readText() } catch (e: Exception) { "" }
                    val inM3u = try { URL("https://iptv-org.github.io/iptv/countries/in.m3u").readText() } catch (e: Exception) { "" }
                    
                    val combinedChannels = parseM3u(tamM3u) + parseM3u(inM3u)
                    // Remove duplicates by stream URL
                    combinedChannels.distinctBy { it.streamUrl }
                }
                
                if (fetchedChannels.isEmpty()) {
                    _error.value = "Live TV not updated. Please check your internet connection or try again later."
                } else {
                    _channels.value = fetchedChannels
                }
            } catch (e: Exception) {
                _error.value = "Failed to load channels: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseM3u(m3uContent: String): List<Channel> {
        val channelsList = mutableListOf<Channel>()
        val lines = m3uContent.lines()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                val nameParts = line.split(",")
                currentName = nameParts.lastOrNull()?.trim() ?: "Unknown"
                
                val logoMatch = "tvg-logo=\"([^\"]+)\"".toRegex().find(line)
                currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                
                val groupMatch = "group-title=\"([^\"]+)\"".toRegex().find(line)
                currentGroup = groupMatch?.groupValues?.get(1) ?: "Other"
            } else if (line.startsWith("http")) {
                val streamUrl = line.trim()
                channelsList.add(Channel(currentName, "Tamil", currentGroup, streamUrl, currentLogo))
            }
        }
        return channelsList
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: ChannelViewModel = viewModel()
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column {
                            // AdMob Banner Unit ID provided by user
                            AdmobBannerAd(adUnitId = "ca-app-pub-4931646089594136/9647553595")
                            BottomNavigationBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") { HomeScreen(navController, viewModel) }
                        composable("categories") { CategoriesScreen(navController, viewModel) }
                        composable("settings") { SettingsScreen() }
                        composable(
                            "player/{channelIndex}",
                            arguments = listOf(navArgument("channelIndex") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val index = backStackEntry.arguments?.getInt("channelIndex") ?: 0
                            PlayerScreen(channelIndex = index, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdmobBannerAd(adUnitId: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp), // Fix fixed height for standard banner
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Home", "home", Icons.Default.Home),
        BottomNavItem("Categories", "categories", Icons.Default.List),
        BottomNavItem("Settings", "settings", Icons.Default.Settings)
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.name) },
                label = { Text(item.name) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val name: String, val route: String, val icon: ImageVector)

@Composable
fun HomeScreen(navController: NavHostController, viewModel: ChannelViewModel) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = error ?: "Unknown error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.fetchChannels() }) {
                    Text("Retry")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Universal TV", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            SectionTitle("Tamil Channels")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(channels) { channel ->
                    ChannelCard(navController, channel, channels)
                }
            }
        }
        item {
            SectionTitle("Entertainment")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(channels.filter { it.category.contains("Entertainment", ignoreCase = true) || it.category.contains("Movies", ignoreCase = true) }) { channel ->
                    ChannelCard(navController, channel, channels)
                }
            }
        }
        item {
            SectionTitle("Live News")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(channels.filter { it.category.contains("News", ignoreCase = true) }) { channel ->
                    ChannelCard(navController, channel, channels)
                }
            }
        }
        item {
            SectionTitle("All Channels")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(channels) { channel ->
                    ChannelCard(navController, channel, channels)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun ChannelCard(navController: NavHostController, channel: Channel, allChannels: List<Channel>) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .size(160.dp, 100.dp)
            .focusable(interactionSource = interactionSource)
            .border(
                width = if (isFocused) 4.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) { 
                val index = allChannels.indexOf(channel)
                navController.navigate("player/$index") 
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            } else {
                Text(
                    text = channel.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CategoriesScreen(navController: NavHostController, viewModel: ChannelViewModel) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val categories = channels.map { it.category }.distinct().filter { it.isNotEmpty() }.sorted()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = error ?: "Unknown error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.fetchChannels() }) {
                    Text("Retry")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Categories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        items(categories) { category ->
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Card(
                modifier = Modifier.fillMaxWidth().height(80.dp)
                    .focusable(interactionSource = interactionSource)
                    .border(
                        width = if (isFocused) 4.dp else 0.dp,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { navController.navigate("home") },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = category,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        SettingsItem("Language: English")
        SettingsItem("Theme: System Default")
        SettingsItem("Video Quality: Auto")
        SettingsItem("Privacy Policy")
        SettingsItem("About")
    }
}

@Composable
fun SettingsItem(title: String) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .focusable(interactionSource = interactionSource)
            .border(
                width = if (isFocused) 4.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {},
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PlayerScreen(channelIndex: Int, viewModel: ChannelViewModel) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val channel = channels.getOrNull(channelIndex)
    
    if (channel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Channel not found")
        }
        return
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "Playing: ${channel.name}",
            color = Color.White,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        )
    }
}
