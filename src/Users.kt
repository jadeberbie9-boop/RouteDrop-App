abstract class User(
    val userId: String,
    protected var name: String,
    protected var emailAddress: String,
    private var password: String,
    private var phoneNumber: String
){
    abstract fun role(): String
    abstract fun display(): String
    fun createPassword(input: String): Boolean = input == password
}
class Customer(
    userId: String,
    name: String,
    password: String,
    emailAddress: String,
    phoneNumber: String
): User(userId, name, emailAddress, password, phoneNumber){
    override fun role(): String {
        return "CUSTOMER"
    }
    override fun display(): String {
        return "name: $name | email address: $emailAddress | user ID: $userId"
    }
}
class Courier(
    userId: String,
    name: String,
    emailAddress: String,
    password: String,
    phoneNumber: String
): User(userId, name, emailAddress, password, phoneNumber){
    override fun role(): String {
        return "COURIER"
    }
    override fun display(): String {
        return "name: $name | email address: $emailAddress | ID: $userId"
    }
}

abstract class Location(
    val locationId: String,
    var locationName: String,
    protected var latitude: Double,
    protected var longitude: Double,
    private val person: String
){
    abstract fun getLocationType(): String
    abstract fun getAddress(): String
    fun coordinates(latitude: Double, longitude: Double) = "$latitude,$longitude"
}
class PickupLoc(
    locationId: String,
    locationName: String,
    latitude: Double,
    longitude: Double,
    person : String
): Location(locationId, locationName, latitude, longitude, person){
    override fun getLocationType(): String {
        return "For pickup"
    }
    override fun getAddress(): String {
        return "Location name: $locationName| Location ID: $locationId "
    }
}
class DropOffLoc(
    locationId: String,
    locationName: String,
    latitude: Double,
    longitude: Double,
    person : String

): Location(locationId, locationName, latitude, longitude, person) {
    override fun getLocationType(): String {
        return "DropOff"
    }

    override fun getAddress(): String {
        return "Location name: $locationName| Location ID: $locationId"
    }

}

class DeliveryDetails(
    val packageDescription: String,
    val instructions: String
) {
    fun displayDetails() {
        println("Package: $packageDescription")
        println("Instructions: $instructions")
    }
}

class Delivery(
    val deliveryId: String,
    val customer: Customer,
    var courier: Courier?,
    val pickup: PickupLoc,
    val dropOff: DropOffLoc,
    private val details: DeliveryDetails
) {
    var status: String = "Pending"

    fun assignCourier(newCourier: Courier) {
        courier = newCourier
        println("Courier assigned successfully.")
    }

    fun updateStatus(newStatus: String) {
        status = newStatus
    }

    fun displayDelivery() {
        println("Delivery ID: $deliveryId")
        println("Customer: ${customer.display()}")
        println("Courier: ${courier?.display() ?: "Not assigned"}")
        println("Pickup: ${pickup.getAddress()}")
        println("Drop-off: ${dropOff.getAddress()}")
        println("Status: $status")
        details.displayDetails()
    }
}