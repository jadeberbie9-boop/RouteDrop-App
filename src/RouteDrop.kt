package com.example.myapplication.routedrop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.location.Geocoder
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.myapplication.map.MapLocationPickerDialog
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// ───── RouteDropModels.kt ─────
abstract class User(
    val userId: String,
    var name: String,
    var emailAddress: String,
    var password: String,
    var phoneNumber: String,
    var address: String = ""
) {
    abstract fun role(): String
    abstract fun display(): String
    fun createPassword(input: String): Boolean = input == password
}

class Customer(
    userId: String,
    name: String,
    password: String,
    emailAddress: String,
    phoneNumber: String,
    address: String = ""
) : User(userId, name, emailAddress, password, phoneNumber, address) {
    override fun role(): String = "CUSTOMER"
    override fun display(): String = "Customer: $name | Email: $emailAddress | ID: $userId"
}

class Courier(
    userId: String,
    name: String,
    emailAddress: String,
    password: String,
    phoneNumber: String,
    address: String = "",
    var vehicleType: String = "Motorcycle"
) : User(userId, name, emailAddress, password, phoneNumber, address) {
    override fun role(): String = "COURIER"
    override fun display(): String = "Courier: $name | Vehicle: $vehicleType | ID: $userId"
}

abstract class Location(
    val locationId: String,
    var locationName: String,
    var latitude: Double,
    var longitude: Double,
    val person: String
) {
    abstract fun getLocationType(): String
    abstract fun getAddress(): String
    fun coordinates(): String = String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
    fun coordinates(lat: Double, lng: Double): String = String.format(Locale.US, "%.4f, %.4f", lat, lng)
}

class PickupLoc(
    locationId: String,
    locationName: String,
    latitude: Double,
    longitude: Double,
    person: String
) : Location(locationId, locationName, latitude, longitude, person) {
    override fun getLocationType(): String = "Pickup"
    override fun getAddress(): String = "$locationName (ID: $locationId) [${coordinates()}]"
}

class DropOffLoc(
    locationId: String,
    locationName: String,
    latitude: Double,
    longitude: Double,
    person: String
) : Location(locationId, locationName, latitude, longitude, person) {
    override fun getLocationType(): String = "Drop-off"
    override fun getAddress(): String = "$locationName (ID: $locationId) [${coordinates()}]"
}

data class DeliveryDetails(
    val packageDescription: String,
    val instructions: String
) {
    fun displayDetails(): String = "Package: $packageDescription | Note: $instructions"
}

class Delivery(
    val deliveryId: String,
    val customer: Customer,
    var courier: Courier?,
    val pickup: PickupLoc,
    val dropOff: DropOffLoc,
    val details: DeliveryDetails,
    val requestedVehicle: String,
    val fare: Double
) {
    var status: String by mutableStateOf("Pending")

    var courierLat: Double by mutableDoubleStateOf(0.0)
    var courierLng: Double by mutableDoubleStateOf(0.0)
    var isCourierLocationAvailable: Boolean by mutableStateOf(false)

    // Auto-calculated ETA String
    var eta: String by mutableStateOf("Calculating...")
    var routePoints: List<GeoPoint> by mutableStateOf(emptyList())

    fun assignCourier(newCourier: Courier) {
        courier = newCourier
        status = "Courier Assigned"
    }

    fun updateStatus(newStatus: String) {
        status = newStatus
        calculateEtaAndRoute()
    }

    fun updateCourierLocation(lat: Double, lng: Double) {
        courierLat = lat
        courierLng = lng
        isCourierLocationAvailable = true
        calculateEtaAndRoute()
    }

    // Uses the OSRM Routing Helper to get true driving ETA and Polylines
    private fun calculateEtaAndRoute() {
        if (!isCourierLocationAvailable) {
            eta = "Waiting for courier location..."
            return
        }

        // If Pending or Delivered, route isn't needed
        if (status == "Pending" || status == "Delivered") {
            eta = "N/A"
            routePoints = emptyList()
            return
        }

        // Target destination switches dynamically!
        val targetLat = if (status == "Courier Assigned") pickup.latitude else dropOff.latitude
        val targetLng = if (status == "Courier Assigned") pickup.longitude else dropOff.longitude

        // Fetch accurate route and ETA in the background
        CoroutineScope(Dispatchers.Main).launch {
            val routeInfo = RoutingHelper.getRoute(courierLat, courierLng, targetLat, targetLng)

            if (routeInfo != null) {
                eta = if (routeInfo.durationMinutes < 1) {
                    "Arriving in less than 1 min"
                } else {
                    "~ ${routeInfo.durationMinutes} min"
                }
                routePoints = routeInfo.geoPoints
            } else {
                // Fallback to straight line if API fails or network drops
                val distanceKm = calculateDistanceKm(courierLat, courierLng, targetLat, targetLng)
                val hours = distanceKm / 30.0
                val totalMinutes = (hours * 60).toInt()

                eta = if (totalMinutes < 1) "Arriving in less than 1 min (Straight-line)" else "~ $totalMinutes min (Straight-line)"
            }
        }
    }
}

fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // Earth's radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

fun calculateFare(distanceKm: Double, vehicleType: String): Double {
    val baseFare = 50.0
    val perKmRate = 15.0
    val multiplier = when (vehicleType) {
        "Motorcycle" -> 1.0
        "Car" -> 1.5
        "Truck" -> 2.5
        else -> 1.0
    }
    return (baseFare + (distanceKm * perKmRate)) * multiplier
}
// ───── AppViewModel.kt ─────
class AppViewModel : ViewModel() {
    val registeredUsers = mutableStateListOf<User>()
    val globalDeliveries = mutableStateListOf<Delivery>()
}
// ───── AuthManager.kt ─────
class AuthManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("route_drop_auth", Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        prefs.edit().apply {
            putString("userId", user.userId)
            putString("name", user.name)
            putString("emailAddress", user.emailAddress)
            putString("password", user.password)
            putString("phoneNumber", user.phoneNumber)
            putString("address", user.address)
            putString("role", user.role())
            if (user is Courier) {
                putString("vehicleType", user.vehicleType)
            }
            putBoolean("isLoggedIn", true)
            apply()
        }
    }

    fun getSavedUser(): User? {
        if (!prefs.getBoolean("isLoggedIn", false)) return null

        val role = prefs.getString("role", "") ?: ""
        val userId = prefs.getString("userId", "") ?: ""
        val name = prefs.getString("name", "") ?: ""
        val email = prefs.getString("emailAddress", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""
        val phone = prefs.getString("phoneNumber", "") ?: ""
        val address = prefs.getString("address", "") ?: ""
        val vehicle = prefs.getString("vehicleType", "Motorcycle") ?: "Motorcycle"

        return if (role == "CUSTOMER") {
            Customer(userId, name, pass, email, phone, address)
        } else if (role == "COURIER") {
            Courier(userId, name, email, pass, phone, address, vehicle)
        } else {
            null
        }
    }

    fun clearUser() {
        prefs.edit().clear().apply()
    }
}
// ───── RoutingHelper.kt ─────
data class RouteInfo(
    val distanceKm: Double,
    val durationMinutes: Int,
    val geoPoints: List<GeoPoint>
)

object RoutingHelper {
    suspend fun getRoute(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ): RouteInfo? = withContext(Dispatchers.IO) {
        try {
            // OSRM API requires coordinates in Longitude,Latitude order
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson"
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // Required: Identify our app to OSRM to comply with free-tier rules
            connection.setRequestProperty("User-Agent", "RouteDrop/1.0 (donnamaepbautista@gmail.com)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val routes = json.getJSONArray("routes")

                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)

                    // Parse precise distance and duration from OSRM
                    val distance = route.getDouble("distance") / 1000.0 // meters to km
                    val duration = (route.getDouble("duration") / 60.0).toInt() // seconds to min

                    // Parse the GeoJSON Polyline array for drawing on the map
                    val geometry = route.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val geoPoints = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        geoPoints.add(GeoPoint(lat, lon))
                    }

                    return@withContext RouteInfo(distance, duration, geoPoints)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
// ───── OsmSetup.kt ─────
object OsmSetup {
    fun init(context: Context) {
        val appContext = context.applicationContext
        val config = Configuration.getInstance()

        // 1. Load existing preferences FIRST
        config.load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        // 2. Set custom User-Agent
        config.userAgentValue = "RouteDrop/1.0 (donnamaepbautista@gmail.com)"

        // 3. Clear ALL old corrupted tile caches (both internal and external)
        val oldTileCache = File(appContext.cacheDir, "osmdroid/tiles")
        if (oldTileCache.exists()) oldTileCache.deleteRecursively()

        val extCache = File(appContext.getExternalFilesDir(null), "osmdroid")
        if (extCache.exists()) extCache.deleteRecursively()

        val oldV2 = File(appContext.cacheDir, "osmdroid/tiles_v2")
        if (oldV2.exists()) oldV2.deleteRecursively()

        // 4. Assign our paths (using a fresh v3 tile cache path to force redownloads)
        config.osmdroidBasePath = File(appContext.cacheDir, "osmdroid")
        config.osmdroidTileCache = File(appContext.cacheDir, "osmdroid/tiles_v3")

        // 5. Save the configuration
        config.save(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }
}
// ───── LocationHelper.kt ─────
class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(
        onResult: (latitude: Double, longitude: Double, address: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                getAddressFromCoordinates(lat, lng) { address ->
                    onResult(lat, lng, address)
                }
            } else {
                onError("Unable to retrieve location. Make sure GPS is enabled.")
            }
        }.addOnFailureListener { exception ->
            onError(exception.localizedMessage ?: "Failed to get location.")
        }
    }

    @Suppress("DEPRECATION")
    fun getAddressFromCoordinates(
        lat: Double,
        lng: Double,
        onAddressResolved: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(context, java.util.Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addressName = addresses.firstOrNull()?.let { addr ->
                            (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                        } ?: "Tagged Location ($lat, $lng)"

                        CoroutineScope(Dispatchers.Main).launch { onAddressResolved(addressName) }
                    }
                } else {
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addressName = addresses?.firstOrNull()?.let { addr ->
                        (0..addr.maxAddressLineIndex).joinToString(", ") { addr.getAddressLine(it) }
                    } ?: "Tagged Location ($lat, $lng)"

                    withContext(Dispatchers.Main) { onAddressResolved(addressName) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onAddressResolved("Tagged Location ($lat, $lng)") }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun searchLocationByName(
        query: String,
        onResult: (latitude: Double, longitude: Double, addressName: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (query.isBlank()) {
            onError("Search query cannot be empty.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(context, java.util.Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(query, 5) { addresses ->
                        val bestMatch = addresses.firstOrNull()
                        CoroutineScope(Dispatchers.Main).launch {
                            if (bestMatch != null) {
                                val addressName = (0..bestMatch.maxAddressLineIndex).joinToString(", ") { bestMatch.getAddressLine(it) }
                                onResult(bestMatch.latitude, bestMatch.longitude, if (addressName.isNotBlank()) addressName else query)
                            } else {
                                onError("No location found for '$query'")
                            }
                        }
                    }
                } else {
                    val addresses = geocoder.getFromLocationName(query, 5)
                    val bestMatch = addresses?.firstOrNull()
                    withContext(Dispatchers.Main) {
                        if (bestMatch != null) {
                            val addressName = (0..bestMatch.maxAddressLineIndex).joinToString(", ") { bestMatch.getAddressLine(it) }
                            onResult(bestMatch.latitude, bestMatch.longitude, if (addressName.isNotBlank()) addressName else query)
                        } else {
                            onError("No location found for '$query'")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Error searching location: ${e.localizedMessage}") }
            }
        }
    }
}
// ───── NotificationHelper.kt ─────
class NotificationHelper(private val context: Context) {
    private val channelId = "routedrop_notifications"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Delivery Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for delivery status changes"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default system icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
// ───── AuthScreens.kt ─────
@Composable
fun LoginScreen(
    registeredUsers: List<User>,
    onLoginSuccess: (User) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onGuestLogin: () -> Unit
) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("RouteDrop", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Log In", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (phoneNumber.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val user = registeredUsers.find { it.phoneNumber == phoneNumber && it.password == password }
                    if (user != null) {
                        onLoginSuccess(user)
                    } else {
                        Toast.makeText(context, "Invalid phone number or password.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log In")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? Sign Up")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onGuestLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue as Guest")
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUpSuccess: (User) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var selectedRole by remember { mutableStateOf("CUSTOMER") }
    var selectedVehicle by remember { mutableStateOf("Motorcycle") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("RouteDrop", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Sign Up", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))

            // Role Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Role: ", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == "CUSTOMER",
                        onClick = { selectedRole = "CUSTOMER" }
                    )
                    Text("Customer")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == "COURIER",
                        onClick = { selectedRole = "COURIER" }
                    )
                    Text("Courier")
                }
            }

            if (selectedRole == "COURIER") {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Vehicle Type:", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Motorcycle", "Car", "Truck").forEach { vehicle ->
                        OutlinedButton(
                            onClick = { selectedVehicle = vehicle },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedVehicle == vehicle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(vehicle)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank() || phoneNumber.isBlank() || address.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please fill in all required fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val newUser: User = if (selectedRole == "CUSTOMER") {
                        Customer(
                            userId = "C-${UUID.randomUUID().toString().take(6)}",
                            name = name,
                            phoneNumber = phoneNumber,
                            emailAddress = emailAddress.ifBlank { "N/A" },
                            address = address,
                            password = password
                        )
                    } else {
                        Courier(
                            userId = "CR-${UUID.randomUUID().toString().take(6)}",
                            name = name,
                            phoneNumber = phoneNumber,
                            emailAddress = emailAddress.ifBlank { "N/A" },
                            address = address,
                            password = password,
                            vehicleType = selectedVehicle
                        )
                    }

                    Toast.makeText(context, "Sign Up Successful!", Toast.LENGTH_SHORT).show()
                    onSignUpSuccess(newUser)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Up")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? Log In")
            }
        }
    }
}
// ───── RouteDropScreen.kt ─────
enum class BottomTab {
    HOME, NEARBY, ME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDropScreen(
    loggedInUser: User,
    globalDeliveries: MutableList<Delivery>,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }

    var reqVehicle by remember { mutableStateOf("Motorcycle") }

    // Notification Helper (Simulates FCM Push Notification)
    val notificationHelper = remember { NotificationHelper(context) }

    // State for Courier tracking Map
    var deliveryToTrack by remember { mutableStateOf<Delivery?>(null) }

    // State for Customer flow
    var pickupName by remember { mutableStateOf(loggedInUser.address) }
    var pickupLat by remember { mutableDoubleStateOf(14.0667) }
    var pickupLng by remember { mutableDoubleStateOf(120.6333) }

    var dropOffName by remember { mutableStateOf("Batangas State University") }
    var dropOffLat by remember { mutableDoubleStateOf(13.7565) }
    var dropOffLng by remember { mutableDoubleStateOf(121.0583) }

    var pkgDesc by remember { mutableStateOf("School Documents") }
    var pkgInstructions by remember { mutableStateOf("Please handle carefully.") }

    var isGeotagging by remember { mutableStateOf(false) }
    var activeGeotagTarget by remember { mutableStateOf<String?>(null) } // "PICKUP" or "DROPOFF"
    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "PICKUP" or "DROPOFF"

    // Live tracking for Courier
    var isLiveTracking by remember { mutableStateOf(false) }

    var currentTab by remember { mutableStateOf(BottomTab.HOME) }

    // Request Notification Permission on Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Technically you'd use a launcher here, but this is a quick safety check
                // to at least log it or prompt gracefully later.
            }
        }
    }

    // Auto-update Courier Location if broadcasting
    LaunchedEffect(isLiveTracking) {
        if (isLiveTracking && loggedInUser is Courier) {
            while (true) {
                locationHelper.fetchCurrentLocation(
                    onResult = { lat, lng, _ ->
                        globalDeliveries
                            .filter { it.courier?.userId == loggedInUser.userId && it.status != "Delivered" }
                            .forEach { delivery ->
                                delivery.updateCourierLocation(lat, lng)
                            }
                    },
                    onError = {}
                )
                kotlinx.coroutines.delay(5000) // update every 5 seconds
            }
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            val target = activeGeotagTarget
            if (target != null) {
                isGeotagging = true
                locationHelper.fetchCurrentLocation(
                    onResult = { lat, lng, addr ->
                        isGeotagging = false
                        if (target == "PICKUP") {
                            pickupLat = lat
                            pickupLng = lng
                            pickupName = addr
                        } else {
                            dropOffLat = lat
                            dropOffLng = lng
                            dropOffName = addr
                        }
                        Toast.makeText(context, "Location tagged successfully!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        isGeotagging = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        } else {
            Toast.makeText(context, "Location permission is required for geotagging.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestGeotag(target: String) {
        activeGeotagTarget = target
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Map Location Picker Dialog launch
    mapPickerTarget?.let { target ->
        val isPickup = target == "PICKUP"
        MapLocationPickerDialog(
            title = if (isPickup) "Pinpoint Pickup Location" else "Pinpoint Drop-off Location",
            initialLat = if (isPickup) pickupLat else dropOffLat,
            initialLng = if (isPickup) pickupLng else dropOffLng,
            onLocationConfirmed = { lat, lng, address ->
                if (isPickup) {
                    pickupLat = lat
                    pickupLng = lng
                    pickupName = address
                } else {
                    dropOffLat = lat
                    dropOffLng = lng
                    dropOffName = address
                }
                mapPickerTarget = null
                Toast.makeText(context, "Pinpoint location selected!", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = {
                mapPickerTarget = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "RouteDrop Delivery",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("🏃‍♂️‍➡️ Log Out", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == BottomTab.HOME,
                    onClick = { currentTab = BottomTab.HOME },
                    icon = { Text("🏠", fontSize = 20.sp) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == BottomTab.NEARBY,
                    onClick = { currentTab = BottomTab.NEARBY },
                    icon = { Text("📍", fontSize = 20.sp) },
                    label = { Text("Nearby") }
                )
                NavigationBarItem(
                    selected = currentTab == BottomTab.ME,
                    onClick = { currentTab = BottomTab.ME },
                    icon = { Text("👤", fontSize = 20.sp) },
                    label = { Text("Me") }
                )
            }
        }
    ) { paddingValues ->
        when (currentTab) {
            BottomTab.HOME -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // Logged In User Info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Logged In As:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(loggedInUser.display(), fontSize = 13.sp)
                        }
                    }

                    if (loggedInUser is Courier) {
                        // Courier Dashboard
                        val myAcceptedDeliveries = globalDeliveries.filter { it.courier?.userId == loggedInUser.userId && it.status != "Delivered" }
                        val availableRequests = globalDeliveries.filter { it.courier == null && it.status == "Pending" }

                        // 1. My Accepted Deliveries Section
                        if (myAcceptedDeliveries.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚚 My Accepted Deliveries", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = { isLiveTracking = !isLiveTracking },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (isLiveTracking) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (isLiveTracking) "🛑 Stop Broadcast" else "📡 Go Live")
                                }
                            }

                            if (isLiveTracking) {
                                Text("📡 Sharing live location with customers...", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                            }

                            myAcceptedDeliveries.forEach { delivery ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Request from: ${delivery.customer.name}", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("₱${"%.2f".format(delivery.fare)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text("📍 ${delivery.pickup.locationName}")
                                        Text("🏁 ${delivery.dropOff.locationName}")
                                        Text("Status: ${delivery.status}", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)

                                        OrderTrackerBar(status = delivery.status)

                                        if (delivery.eta != "N/A" && delivery.eta != "Calculating...") {
                                            Text("⏱️ ETA: ${delivery.eta}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    delivery.updateStatus("In Transit")
                                                    notificationHelper.sendNotification("🚚 Courier is on the way!", "Your package is now in transit.")
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("In Transit")
                                            }
                                            Button(
                                                onClick = {
                                                    delivery.updateStatus("Delivered")
                                                    notificationHelper.sendNotification("✅ Package Delivered", "Your package has arrived safely.")
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Delivered")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                isGeotagging = true
                                                locationHelper.fetchCurrentLocation(
                                                    onResult = { lat, lng, _ ->
                                                        isGeotagging = false
                                                        delivery.updateCourierLocation(lat, lng)
                                                        Toast.makeText(context, "Location shared to customer!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onError = { err ->
                                                        isGeotagging = false
                                                        Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isGeotagging) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            } else {
                                                Text("📍 Share Single Location Update")
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // 2. Available Requests Section
                        Text("📋 Available Customer Requests", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (availableRequests.isEmpty()) {
                            Text("No pending delivery requests right now.", fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        } else {
                            availableRequests.forEach { delivery ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Customer: ${delivery.customer.name}", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("₱${"%.2f".format(delivery.fare)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text("📍 Pickup: ${delivery.pickup.locationName}")
                                        Text("🏁 Drop-off: ${delivery.dropOff.locationName}")
                                        Text("Package: ${delivery.details.packageDescription} (${delivery.requestedVehicle})", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = { delivery.assignCourier(loggedInUser) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Accept Delivery Request")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Customer Request Flow

                        // Pickup Geotag Section
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📍 Pickup Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = pickupName,
                                    onValueChange = { pickupName = it },
                                    label = { Text("Pickup Location Name/Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Coords: %.4f, %.4f".format(pickupLat, pickupLng),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { mapPickerTarget = "PICKUP" },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗺️ Pick on Map", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { requestGeotag("PICKUP") },
                                        enabled = !isGeotagging,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📍 Tag GPS", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Dropoff Geotag Section
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🏁 Drop-off Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = dropOffName,
                                    onValueChange = { dropOffName = it },
                                    label = { Text("Drop-off Location Name/Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Coords: %.4f, %.4f".format(dropOffLat, dropOffLng),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { mapPickerTarget = "DROPOFF" },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗺️ Pick on Map", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { requestGeotag("DROPOFF") },
                                        enabled = !isGeotagging,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📍 Tag GPS", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Package Details Section
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📦 Delivery Package Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = pkgDesc,
                                    onValueChange = { pkgDesc = it },
                                    label = { Text("Package Description") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = pkgInstructions,
                                    onValueChange = { pkgInstructions = it },
                                    label = { Text("Instructions") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Requested Vehicle:", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf("Motorcycle", "Car", "Truck").forEach { vehicle ->
                                        OutlinedButton(
                                            onClick = { reqVehicle = vehicle },
                                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (reqVehicle == vehicle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Text(vehicle)
                                        }
                                    }
                                }
                            }
                        }

                        // Loading Indicator during Geotagging
                        AnimatedVisibility(visible = isGeotagging) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Fetching high-accuracy GPS coordinates...", fontSize = 14.sp)
                            }
                        }

                        // Calculate estimated fare dynamically
                        val estimatedDistance = calculateDistanceKm(pickupLat, pickupLng, dropOffLat, dropOffLng)
                        val estimatedFare = calculateFare(estimatedDistance, reqVehicle)

                        // Fare display
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimated Fare:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.weight(1f))
                                Text("₱${"%.2f".format(estimatedFare)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Create Delivery Button
                        Button(
                            onClick = {
                                val pLoc = PickupLoc("P-${globalDeliveries.size}", pickupName, pickupLat, pickupLng, loggedInUser.name)
                                val dLoc = DropOffLoc("D-${globalDeliveries.size}", dropOffName, dropOffLat, dropOffLng, loggedInUser.name)
                                val details = DeliveryDetails(pkgDesc, pkgInstructions)
                                val delivery = Delivery("DEL${globalDeliveries.size}", loggedInUser as Customer, null, pLoc, dLoc, details, reqVehicle, estimatedFare)

                                globalDeliveries.add(0, delivery) // Add to top of list

                                // Reset form fields
                                pkgDesc = ""
                                pkgInstructions = ""

                                Toast.makeText(context, "Delivery Request Created!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚀 Create Delivery Request")
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Your Active Deliveries", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                        // Multiple active deliveries for Customer
                        val myDeliveries = globalDeliveries.filter { it.customer.userId == loggedInUser.userId }
                        if (myDeliveries.isEmpty()) {
                            Text("No active requests.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        } else {
                            myDeliveries.forEach { delivery ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Delivery [${delivery.deliveryId}]",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("₱${"%.2f".format(delivery.fare)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            StatusBadge(status = delivery.status)
                                        }

                                        OrderTrackerBar(status = delivery.status)

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("Courier: ${delivery.courier?.name ?: "Looking for courier..."}")
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("📍 Pickup: ${delivery.pickup.getAddress()}")
                                        Text("🏁 Drop-off: ${delivery.dropOff.getAddress()}")

                                        if (delivery.isCourierLocationAvailable && delivery.status != "Delivered") {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("⏱️ ETA: ${delivery.eta}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { deliveryToTrack = delivery },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("🗺️ Live Map Tracking")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } // Close Customer Request flow block
                } // Close Column
            } // Close HOME tab
            BottomTab.NEARBY -> {
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    com.example.myapplication.map.LocationSearchMapScreen()
                }
            }
            BottomTab.ME -> {
                ProfileScreen(loggedInUser = loggedInUser, globalDeliveries = globalDeliveries, paddingValues = paddingValues)
            }
        } // Close when block
    } // Close Scaffold

    // Tracking UI Overlay
    deliveryToTrack?.let { delivery ->
        CourierMapDialog(
            delivery = delivery,
            onDismissRequest = { deliveryToTrack = null }
        )
    }
}

@Composable
fun ProfileScreen(loggedInUser: User, globalDeliveries: List<Delivery>, paddingValues: PaddingValues) {
    var name by remember { mutableStateOf(loggedInUser.name) }
    var phone by remember { mutableStateOf(loggedInUser.phoneNumber) }
    var address by remember { mutableStateOf(loggedInUser.address) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("My Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Default Location/Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                loggedInUser.name = name
                loggedInUser.phoneNumber = phone
                loggedInUser.address = address

                // Save updated info so they don't lose it on restart
                val authManager = AuthManager(context)
                authManager.saveUser(loggedInUser)

                Toast.makeText(context, "Profile Updated successfully!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Information")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Delivery History", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        val pastDeliveries = globalDeliveries.filter {
            (it.customer.userId == loggedInUser.userId || it.courier?.userId == loggedInUser.userId) && it.status == "Delivered"
        }

        if (pastDeliveries.isEmpty()) {
            Text("No past deliveries yet.", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        } else {
            pastDeliveries.forEach { delivery ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Delivery [${delivery.deliveryId}]", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("✅ Delivered", color = Color(0xFF1AC022), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📍 From: ${delivery.pickup.locationName}")
                        Text("🏁 To: ${delivery.dropOff.locationName}")
                        Text("📦 Package: ${delivery.details.packageDescription}", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderTrackerBar(status: String) {
    val steps = listOf("Pending", "Accepted", "In Transit", "Delivered")
    val currentStepIndex = when (status) {
        "Pending" -> 0
        "Courier Assigned" -> 1
        "In Transit" -> 2
        "Delivered" -> 3
        else -> 0
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, label ->
            val isCompleted = index <= currentStepIndex
            val isCurrent = index == currentStepIndex
            val circleColor = if (isCompleted) MaterialTheme.colorScheme.primary else Color.LightGray

            // Step item
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // The connecting lines
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(3.dp).background(if (index == 0) Color.Transparent else if (index <= currentStepIndex) MaterialTheme.colorScheme.primary else Color.LightGray))
                        Box(modifier = Modifier.weight(1f).height(3.dp).background(if (index == steps.size - 1) Color.Transparent else if (index < currentStepIndex) MaterialTheme.colorScheme.primary else Color.LightGray))
                    }
                    // The circle
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val bgColor = when (status) {
        "Pending" -> Color(0xFFED6C02)
        "Courier Assigned" -> Color(0xFF0288D1)
        "Picked Up" -> Color(0xFF0288D1)
        "In Transit" -> Color(0xFF9C27B0)
        "Delivered" -> Color(0xFF1AC022)
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CourierMapDialog(
    delivery: Delivery,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val targetLat = if (delivery.status == "Courier Assigned") delivery.pickup.latitude else delivery.dropOff.latitude
    val targetLng = if (delivery.status == "Courier Assigned") delivery.pickup.longitude else delivery.dropOff.longitude
    val targetName = if (delivery.status == "Courier Assigned") "Pickup" else "Drop-off"

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Track the last known location so we don't force snap the user when they pan the map
        var lastCourierLat by remember { mutableDoubleStateOf(delivery.courierLat) }
        var lastCourierLng by remember { mutableDoubleStateOf(delivery.courierLng) }

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
                        text = "Courier Real-Time Tracking",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Map View
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
                                controller.setZoom(16.0)

                                val courierGeoPoint = GeoPoint(delivery.courierLat, delivery.courierLng)
                                controller.setCenter(courierGeoPoint)

                                val marker = Marker(this).apply {
                                    position = courierGeoPoint
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Courier Location"
                                    snippet = "Lat: %.4f, Lng: %.4f".format(delivery.courierLat, delivery.courierLng)
                                }
                                overlays.add(marker)
                                mapViewRef = this
                            }
                        },
                        update = { mapView ->
                            val courierGeoPoint = GeoPoint(delivery.courierLat, delivery.courierLng)

                            // Only animate if the courier actually moved
                            if (delivery.courierLat != lastCourierLat || delivery.courierLng != lastCourierLng) {
                                mapView.controller.animateTo(courierGeoPoint)
                                lastCourierLat = delivery.courierLat
                                lastCourierLng = delivery.courierLng
                            }

                            // Clear map
                            mapView.overlays.removeAll { it is Marker || it is Polyline }

                            // 1. Draw Route Polyline (if we have one)
                            if (delivery.routePoints.isNotEmpty()) {
                                val polyline = Polyline().apply {
                                    setPoints(delivery.routePoints)
                                    outlinePaint.color = android.graphics.Color.parseColor("#0288D1") // Blue
                                    outlinePaint.strokeWidth = 12f
                                }
                                mapView.overlays.add(polyline)
                            }

                            // 2. Draw Target Destination Marker
                            val targetMarker = Marker(mapView).apply {
                                position = GeoPoint(targetLat, targetLng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = targetName
                            }
                            mapView.overlays.add(targetMarker)

                            // 3. Draw Courier Marker
                            val courierMarker = Marker(mapView).apply {
                                position = courierGeoPoint
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Courier Location"
                                snippet = delivery.eta
                            }
                            mapView.overlays.add(courierMarker)

                            mapView.invalidate()
                        }
                    )
                }

                // Footer details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Tracking Courier: ${delivery.courier?.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Coords: %.5f, %.5f".format(delivery.courierLat, delivery.courierLng),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Map")
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
