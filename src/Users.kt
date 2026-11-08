
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
    address: String = ""
) : User(userId, name, emailAddress, password, phoneNumber, address) {
    override fun role(): String = "COURIER"
    override fun display(): String = "Courier: $name | Email: $emailAddress | ID: $userId"
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
    val details: DeliveryDetails
) {
    var status: String by mutableStateOf("Pending")

    var courierLat: Double by mutableDoubleStateOf(0.0)
    var courierLng: Double by mutableDoubleStateOf(0.0)
    var isCourierLocationAvailable: Boolean by mutableStateOf(false)

    fun assignCourier(newCourier: Courier) {
        courier = newCourier
        status = "Courier Assigned"
    }

    fun updateStatus(newStatus: String) {
        status = newStatus
    }

    fun updateCourierLocation(lat: Double, lng: Double) {
        courierLat = lat
        courierLng = lng
        isCourierLocationAvailable = true
    }
}
