package ro.softwarechef.freshboomer.models

data class Contact(
    val id: String = "",
    val name: String,
    val phoneNumber: String,
    val lookupKey: String = "",
    val lastMessage: String = "",
    val date: Long = 0,
    var unreadCount: Int = 0
) 