package com.shilapi.xcertplay.hud

/** Next-turn instruction for the dashboard overlay. Icons use the AMap NEW_ICON vocabulary. */
data class ClusterTurnGuidance(
    val icon: Int,
    val roundaboutExit: Int,
    val distanceMeters: Int,
    val road: String,
    /** Route-level arrival/duration/distance for the info strip below the card. */
    val arrivalEpochSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val remainingMeters: Long? = null,
) {
    companion object {
        internal fun from(frame: BydClusterFrame): ClusterTurnGuidance {
            return ClusterTurnGuidance(
                icon = frame.icon,
                roundaboutExit = frame.roundaboutExit,
                distanceMeters = frame.distanceMeters,
                road = frame.road,
                arrivalEpochSeconds = frame.arrivalEpochSeconds,
                remainingSeconds = frame.routeRemainingSeconds.takeIf { it >= 0 }?.toLong(),
                remainingMeters = frame.routeRemainingMeters.takeIf { it >= 0 }?.toLong(),
            )
        }
    }
}
