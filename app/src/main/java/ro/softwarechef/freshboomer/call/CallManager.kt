package ro.softwarechef.freshboomer.call

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object CallManager {
    var currentCall by mutableStateOf<Call?>(null)
        private set

    @Suppress("DEPRECATION")
    var callState by mutableStateOf(Call.STATE_NEW)
        private set

    var phoneNumber by mutableStateOf("")
        private set

    var displayName by mutableStateOf<String?>(null)
        private set

    private var appContext: Context? = null

    @Suppress("DEPRECATION")
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, newState: Int) {
            Log.d("CallManager", "Call state changed: $newState")
            callState = newState

            // Retry extracting number/name if not yet resolved
            if (phoneNumber.isBlank()) {
                phoneNumber = call.details?.handle?.schemeSpecificPart ?: ""
                if (phoneNumber.isNotBlank() && displayName == null) {
                    displayName = appContext?.let { lookupContactName(it, phoneNumber) }
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            // Details may arrive after onCallAdded — extract number and name here too
            val number = details.handle?.schemeSpecificPart ?: ""
            if (number.isNotBlank() && phoneNumber != number) {
                phoneNumber = number
                displayName = appContext?.let { lookupContactName(it, phoneNumber) }
            } else if (number.isNotBlank() && displayName == null) {
                displayName = appContext?.let { lookupContactName(it, phoneNumber) }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun updateCall(call: Call?, context: Context? = null) {
        currentCall?.unregisterCallback(callCallback)
        currentCall = call
        appContext = context ?: appContext
        if (call != null) {
            call.registerCallback(callCallback)
            callState = call.state
            phoneNumber = call.details?.handle?.schemeSpecificPart ?: ""
            displayName = if (phoneNumber.isNotBlank()) {
                appContext?.let { lookupContactName(it, phoneNumber) }
            } else null
        } else {
            callState = Call.STATE_NEW
            phoneNumber = ""
            displayName = null
        }
    }

    fun answer() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        currentCall?.disconnect()
    }

    fun reject() {
        currentCall?.reject(false, null)
    }

    fun lookupContactName(context: Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("CallManager", "Contact lookup failed", e)
            null
        }
    }
}
