package com.mapbox.navigation.core.replay

import android.app.PendingIntent
import android.os.Looper
import androidx.annotation.UiThread
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.navigation.core.replay.history.ReplayEventBase
import com.mapbox.navigation.core.replay.history.ReplayEventUpdateLocation
import com.mapbox.navigation.core.replay.history.ReplayEventsObserver
import com.mapbox.navigation.core.replay.history.mapToLocation
import java.util.concurrent.CopyOnWriteArrayList

private typealias EngineCallback = LocationEngineCallback<LocationEngineResult>

/**
 * Location Engine for replaying route history.
 */
@UiThread
class ReplayLocationEngine(
    private val mapboxReplayer: MapboxReplayer
) : LocationEngine, ReplayEventsObserver {

    private val registeredCallbacks: MutableList<EngineCallback> = CopyOnWriteArrayList()
    private val lastLocationCallbacks: MutableList<EngineCallback> = mutableListOf()
    private var lastLocationEngineResult: LocationEngineResult? = null

    init {
        mapboxReplayer.registerObserver(this)
    }

    /**
     * Requests location updates with a callback on the specified Looper thread.
     */
    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: EngineCallback,
        looper: Looper?
    ) {
        registeredCallbacks.add(callback)
    }

    /**
     * Removes location updates for the given location engine callback.
     *
     * It is recommended to remove location requests when the activity is in a paused or
     * stopped state, doing so helps battery performance.
     */
    override fun removeLocationUpdates(callback: EngineCallback) {
        registeredCallbacks.remove(callback)
    }

    /**
     * Returns the most recent location currently available.
     *
     * If a location is not available, which should happen very rarely, null will be returned.
     */
    override fun getLastLocation(callback: EngineCallback) {
        if (lastLocationEngineResult != null) {
            callback.onSuccess(lastLocationEngineResult)
        } else {
            lastLocationCallbacks.add(callback)
        }
    }

    /**
     * Requests location updates with callback on the specified PendingIntent.
     */
    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent?
    ) {
        throw UnsupportedOperationException("requestLocationUpdates with intents is unsupported")
    }

    /**
     * Removes location updates for the given pending intent.
     *
     * It is recommended to remove location requests when the activity is in a paused or
     * stopped state, doing so helps battery performance.
     */
    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        throw UnsupportedOperationException("removeLocationUpdates with intents is unsupported")
    }

    override fun replayEvents(replayEvents: List<ReplayEventBase>) {
        replayEvents.forEach { replayEventBase ->
            when (replayEventBase) {
                is ReplayEventUpdateLocation -> replayLocation(replayEventBase)
            }
        }
    }

    internal fun cleanUpLastLocation() {
        lastLocationEngineResult = null
    }

    private fun replayLocation(event: ReplayEventUpdateLocation) {
        val eventLocation = event.location
        val location = eventLocation.mapToLocation(
            eventTimeOffset = mapboxReplayer.eventRealtimeOffset(event.eventTimestamp)
        )
        val locationEngineResult = LocationEngineResult.create(location)
        lastLocationEngineResult = locationEngineResult

        registeredCallbacks.forEach { it.onSuccess(locationEngineResult) }
        lastLocationCallbacks.forEach { it.onSuccess(locationEngineResult) }
        lastLocationCallbacks.clear()
    }
}
