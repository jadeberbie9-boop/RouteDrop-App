package com.example.myapplication.routedrop

import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

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
