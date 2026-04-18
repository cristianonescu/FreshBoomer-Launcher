package ro.softwarechef.freshboomer.data

/**
 * Pure decision logic for missed-call announcements. Extracted from
 * `ImmersiveActivity.refreshLastCall()` so it can be unit-tested without
 * Android `Context` / `SharedPreferences` / TTS coupling.
 *
 * Rule: announce the most recent missed call up to [maxAnnouncements] times
 * per unique caller number. A new caller (different from the last-announced
 * one) resets the counter.
 */
object MissedCallAnnouncer {

    data class Decision(
        /** Whether the caller should be announced now. */
        val shouldAnnounce: Boolean,
        /**
         * Updated announcement count for the current caller after this decision.
         * When there is no current caller, this is 0.
         */
        val newCount: Int,
        /**
         * Number that should be persisted as "last announced number" going
         * forward. Null when there is no current caller to track.
         */
        val newLastAnnouncedNumber: String?
    )

    /**
     * @param currentCallNumber the number of the most recent missed call, or
     *   null if there is no missed call to consider.
     * @param lastAnnouncedNumber the number persisted from the previous
     *   decision, or null if none has been announced yet.
     * @param storedCount the announcement count persisted alongside
     *   [lastAnnouncedNumber].
     * @param maxAnnouncements the upper bound on announcements per unique
     *   caller. Values <= 0 mean "never announce".
     */
    fun decide(
        currentCallNumber: String?,
        lastAnnouncedNumber: String?,
        storedCount: Int,
        maxAnnouncements: Int
    ): Decision {
        if (currentCallNumber == null) {
            return Decision(shouldAnnounce = false, newCount = 0, newLastAnnouncedNumber = null)
        }

        val isNewCaller = currentCallNumber != lastAnnouncedNumber
        val currentCount = if (isNewCaller) 0 else storedCount

        return if (currentCount < maxAnnouncements) {
            Decision(
                shouldAnnounce = true,
                newCount = currentCount + 1,
                newLastAnnouncedNumber = currentCallNumber
            )
        } else {
            Decision(
                shouldAnnounce = false,
                newCount = currentCount,
                newLastAnnouncedNumber = currentCallNumber
            )
        }
    }
}
