package ro.softwarechef.freshboomer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import ro.softwarechef.freshboomer.models.QuickContact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object QuickContactRepository {

    private const val FILENAME = "quick_contacts.json"
    private const val PHOTO_DIR = "contact_photos"

    private var cache: MutableList<QuickContact>? = null

    fun getContacts(context: Context): List<QuickContact> {
        cache?.let { return it.toList() }

        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) {
            return emptyList()
        }

        val json = file.readText()
        val array = JSONArray(json)
        val contacts = mutableListOf<QuickContact>()
        for (i in 0 until array.length()) {
            contacts.add(fromJson(array.getJSONObject(i)))
        }
        contacts.sortBy { it.sortOrder }
        cache = contacts
        return contacts.toList()
    }

    fun ensureDefaults(context: Context) {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) {
            seedDefaults(context)
        }
    }

    fun saveContacts(context: Context, contacts: List<QuickContact>) {
        val array = JSONArray()
        contacts.forEach { array.put(toJson(it)) }
        File(context.filesDir, FILENAME).writeText(array.toString(2))
        cache = contacts.toMutableList()
    }

    fun addContact(context: Context, contact: QuickContact) {
        val contacts = getContacts(context).toMutableList()
        contacts.add(contact)
        reindex(contacts)
        saveContacts(context, contacts)
    }

    fun updateContact(context: Context, contact: QuickContact) {
        val contacts = getContacts(context).toMutableList()
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            contacts[index] = contact
            saveContacts(context, contacts)
        }
    }

    fun removeContact(context: Context, id: String) {
        val contacts = getContacts(context).toMutableList()
        val removed = contacts.find { it.id == id }
        contacts.removeAll { it.id == id }
        reindex(contacts)
        saveContacts(context, contacts)
        // Delete associated photo if it's in internal storage
        removed?.photoUri?.let { deletePhoto(it) }
    }

    fun moveContact(context: Context, id: String, direction: Int) {
        val contacts = getContacts(context).toMutableList()
        val index = contacts.indexOfFirst { it.id == id }
        if (index < 0) return
        val newIndex = (index + direction).coerceIn(0, contacts.lastIndex)
        if (newIndex == index) return
        val item = contacts.removeAt(index)
        contacts.add(newIndex, item)
        reindex(contacts)
        saveContacts(context, contacts)
    }

    fun savePhotoToInternal(context: Context, sourceUri: Uri): String {
        val photoDir = File(context.filesDir, PHOTO_DIR)
        if (!photoDir.exists()) photoDir.mkdirs()

        val fileName = "${UUID.randomUUID()}.jpg"
        val destFile = File(photoDir, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        return destFile.absolutePath
    }

    private fun deletePhoto(photoUri: String) {
        val file = File(photoUri)
        if (file.exists()) file.delete()
    }

    fun seedDefaults(context: Context) {
        saveContacts(context, emptyList())
    }

    private fun reindex(contacts: MutableList<QuickContact>) {
        contacts.forEachIndexed { index, contact ->
            contacts[index] = contact.copy(sortOrder = index)
        }
    }

    /**
     * Decodes a base64-encoded photo string and saves it to internal storage.
     * Returns the absolute file path, or null if decoding fails.
     */
    fun saveBase64PhotoToInternal(context: Context, base64Data: String, mime: String?): String? {
        return try {
            val photoDir = File(context.filesDir, PHOTO_DIR)
            if (!photoDir.exists()) photoDir.mkdirs()

            val extension = when (mime) {
                "image/png" -> "png"
                else -> "jpg"
            }
            val fileName = "${UUID.randomUUID()}.$extension"
            val destFile = File(photoDir, fileName)

            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            destFile.writeBytes(bytes)
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("QuickContactRepo", "Failed to decode base64 photo", e)
            null
        }
    }

    private fun toJson(contact: QuickContact): JSONObject {
        return JSONObject().apply {
            put("id", contact.id)
            put("name", contact.name)
            put("phoneNumber", contact.phoneNumber)
            put("photoUri", contact.photoUri ?: JSONObject.NULL)
            put("drawableResName", contact.drawableResName ?: JSONObject.NULL)
            put("sortOrder", contact.sortOrder)
        }
    }

    private fun fromJson(obj: JSONObject): QuickContact {
        return QuickContact(
            id = obj.getString("id"),
            name = obj.getString("name"),
            phoneNumber = obj.getString("phoneNumber"),
            photoUri = obj.optString("photoUri", "").takeIf { it != "null" && it.isNotEmpty() },
            drawableResName = obj.optString("drawableResName", "").takeIf { it != "null" && it.isNotEmpty() },
            sortOrder = obj.getInt("sortOrder")
        )
    }

    fun invalidateCache() {
        cache = null
    }
}
