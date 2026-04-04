package ro.softwarechef.freshboomer.models

data class QuickContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val drawableResName: String? = null,
    val sortOrder: Int = 0
)
