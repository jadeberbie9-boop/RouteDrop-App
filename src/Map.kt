package com.example.myapplication.map

import android.content.Context
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.routedrop.LocationHelper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

// ───── LocationSearchMapScreen.kt ─────
data class SearchResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchMapScreen(
    modifier: Modifier = Modifier,
    defaultCenter: GeoPoint = GeoPoint(40.7128, -74.0060) // Default to NYC, adjust as needed
) {
    val context = LocalContext.current

    // State variables for the search bar and results
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // State to hold the currently selected location to show on the map
    var selectedLocation by remember { mutableStateOf<SearchResult?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val locationHelper = remember { com.example.myapplication.routedrop.LocationHelper(context) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            isFetchingLocation = true
            locationHelper.fetchCurrentLocation(
                onResult = { lat, lng, addr ->
                    isFetchingLocation = false
                    selectedLocation = SearchResult(addr, lat, lng)
                    Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    isFetchingLocation = false
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search Bar UI
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query

                searchJob?.cancel()

                if (query.length > 2) {
                    searchJob = coroutineScope.launch {
                        delay(1000)
                        isSearching = true
                        errorMessage = null
                        try {
                            val results = searchNominatim(query, "RouteDrop/1.0 (donnamaepbautista@gmail.com)")
                            searchResults = results
                            if (results.isEmpty()) {
                                errorMessage = "No results found"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Network error: ${e.message}"
                        } finally {
                            isSearching = false
                        }
                    }
                } else {
                    searchResults = emptyList()
                    errorMessage = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            placeholder = { Text("Search location...") },
            singleLine = true
        )

        // Show Loading Indicator or Error Messages
        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        } else if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Dropdown-like list of Search Results
        if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
            ) {
                items(searchResults) { result ->
                    ListItem(
                        headlineContent = { Text(result.displayName) },
                        modifier = Modifier.clickable {
                            selectedLocation = result
                            searchResults = emptyList()
                            searchQuery = result.displayName
                        }
                    )
                }
            }
        }

        // Osmdroid Map View
        var lastAnimatedLocation by remember { mutableStateOf<SearchResult?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(12.0)
                        controller.setCenter(defaultCenter)
                    }
                },
                update = { mapView ->
                    selectedLocation?.let { location ->
                        if (location != lastAnimatedLocation) {
                            val geoPoint = GeoPoint(location.lat, location.lon)
                            mapView.controller.animateTo(geoPoint)
                            mapView.controller.setZoom(16.0)

                            mapView.overlays.removeAll { it is Marker }
                            val marker = Marker(mapView).apply {
                                position = geoPoint
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = location.displayName
                            }
                            mapView.overlays.add(marker)
                            mapView.invalidate()

                            lastAnimatedLocation = location
                        }
                    }
                }
            )

            // My Location Pointer Button
            FloatingActionButton(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("📍", fontSize = 24.sp)
                }
            }
        }
    }
}

/**
 * Helper function to hit the Nominatim free geocoding API.
 */
suspend fun searchNominatim(query: String, userAgent: String): List<SearchResult> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5"
    val url = URL(urlString)
    val connection = url.openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Referer", "android-app://$userAgent")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val results = mutableListOf<SearchResult>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                results.add(
                    SearchResult(
                        displayName = item.getString("display_name"),
                        lat = item.getString("lat").toDouble(),
                        lon = item.getString("lon").toDouble()
                    )
                )
            }
            results
        } else {
            throw Exception("Server returned ${connection.responseCode}")
        }
    } finally {
        connection.disconnect()
    }
}
// ───── MapLocationPicker.kt ─────
@Composable
fun MapLocationPickerDialog(
    title: String,
    initialLat: Double,
    initialLng: Double,
    onLocationConfirmed: (latitude: Double, longitude: Double, addressName: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var pinnedLat by remember { mutableDoubleStateOf(initialLat) }
    var pinnedLng by remember { mutableDoubleStateOf(initialLng) }
    var addressName by remember { mutableStateOf("Fetching address...") }
    var searchQuery by remember { mutableStateOf("") }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    var isFetchingLocation by remember { mutableStateOf(false) }

    fun updatePinAndCamera(lat: Double, lng: Double, address: String? = null, forceCenter: Boolean = false) {
        pinnedLat = lat
        pinnedLng = lng
        val geoPoint = GeoPoint(lat, lng)

        mapViewRef?.let { mapView ->
            if (forceCenter) {
                mapView.controller.animateTo(geoPoint)
            }
            markerRef?.let { marker ->
                marker.position = geoPoint
                marker.snippet = "Lat: %.4f, Lng: %.4f".format(lat, lng)
            }
            mapView.invalidate()
        }

        if (address != null) {
            addressName = address
        } else {
            locationHelper.getAddressFromCoordinates(lat, lng) { resolvedAddr ->
                addressName = resolvedAddr
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            isFetchingLocation = true
            locationHelper.fetchCurrentLocation(
                onResult = { lat, lng, addr ->
                    isFetchingLocation = false
                    // update pin and center the map on the user's location
                    updatePinAndCamera(lat, lng, addr, forceCenter = true)
                    Toast.makeText(context, "Location tagged!", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    isFetchingLocation = false
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun performSearch() {
        keyboardController?.hide()
        if (searchQuery.isBlank()) return

        locationHelper.searchLocationByName(
            query = searchQuery,
            onResult = { lat, lng, resolvedAddress ->
                updatePinAndCamera(lat, lng, resolvedAddress, forceCenter = true)
                Toast.makeText(context, "Location found!", Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        )
    }

    LaunchedEffect(pinnedLat, pinnedLng) {
        locationHelper.getAddressFromCoordinates(pinnedLat, pinnedLng) { resolvedAddr ->
            addressName = resolvedAddr
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Location Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search location (e.g. Batangas, SM Manila)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() })
                    )
                    Button(onClick = { performSearch() }) {
                        Text("Search")
                    }
                }

                // OpenStreetMap Canvas Overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                val initialGeo = GeoPoint(initialLat, initialLng)
                                controller.setCenter(initialGeo)

                                val marker = Marker(this).apply {
                                    position = initialGeo
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    this.title = "Selected Pinpoint"
                                }
                                overlays.add(marker)
                                markerRef = marker

                                val eventsReceiver = object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        updatePinAndCamera(p.latitude, p.longitude)
                                        return true
                                    }

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        updatePinAndCamera(p.latitude, p.longitude)
                                        return true
                                    }
                                }
                                overlays.add(MapEventsOverlay(eventsReceiver))

                                mapViewRef = this
                            }
                        },
                        update = { mapView ->
                            // Do nothing on regular updates so the map doesn't snap back when panning
                            mapViewRef = mapView
                        }
                    )

                    // Helper Overlay Instruction
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Text(
                            "Tap map or use search bar to pinpoint location",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // My Location Pointer Button
                    FloatingActionButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("📍", fontSize = 24.sp)
                        }
                    }
                }

                // Selected Coordinates & Address Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍 Selected Address:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(addressName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Coords: %.5f, %.5f".format(pinnedLat, pinnedLng),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onLocationConfirmed(
                                    pinnedLat,
                                    pinnedLng,
                                    addressName
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirm Pinpoint")
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }
}
