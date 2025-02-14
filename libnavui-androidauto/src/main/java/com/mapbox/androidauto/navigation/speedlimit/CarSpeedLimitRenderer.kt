package com.mapbox.androidauto.navigation.speedlimit

import android.graphics.Rect
import android.location.Location
import androidx.annotation.VisibleForTesting
import com.mapbox.androidauto.MapboxCarContext
import com.mapbox.androidauto.MapboxCarOptions
import com.mapbox.androidauto.internal.extensions.mapboxNavigationForward
import com.mapbox.androidauto.internal.logAndroidAuto
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.androidauto.MapboxCarMapObserver
import com.mapbox.maps.extension.androidauto.MapboxCarMapSurface
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.speed.model.SpeedLimitSign
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

/**
 * Create a speed limit sign. This class is demonstrating how to create a renderer.
 * To Create a new speed limit sign experience, try creating a new class.
 */
@OptIn(MapboxExperimental::class)
class CarSpeedLimitRenderer
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal constructor(
    private val services: CarSpeedLimitServices,
    private val options: MapboxCarOptions,
) : MapboxCarMapObserver {

    /**
     * Public constructor and the internal constructor is for unit testing.
     */
    constructor(mapboxCarContext: MapboxCarContext) : this(
        CarSpeedLimitServices(),
        mapboxCarContext.options
    )

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var speedLimitWidget: SpeedLimitWidget? = null

    private var distanceFormatterOptions: DistanceFormatterOptions? = null
    private val navigationObserver = mapboxNavigationForward(this::onAttached, this::onDetached)

    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            updateSpeed(locationMatcherResult)
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no op
        }
    }

    private lateinit var scope: CoroutineScope

    private fun onAttached(mapboxNavigation: MapboxNavigation) {
        distanceFormatterOptions = mapboxNavigation
            .navigationOptions.distanceFormatterOptions
        mapboxNavigation.registerLocationObserver(locationObserver)
    }

    private fun onDetached(mapboxNavigation: MapboxNavigation) {
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        distanceFormatterOptions = null
    }

    private fun updateSpeed(locationMatcherResult: LocationMatcherResult) {
        val speedKmph =
            locationMatcherResult.enhancedLocation.speed / METERS_IN_KILOMETER * SECONDS_IN_HOUR
        val speedLimitOptions = options.speedLimitOptions.value
        val signFormat = speedLimitOptions.forcedSignFormat
            ?: locationMatcherResult.speedLimit?.speedLimitSign
        val threshold = speedLimitOptions.warningThreshold
        when (distanceFormatterOptions!!.unitType) {
            UnitType.IMPERIAL -> {
                val speedLimit =
                    locationMatcherResult.speedLimit?.speedKmph?.let { speedLimitKmph ->
                        5 * (speedLimitKmph / KILOMETERS_IN_MILE / 5).roundToInt()
                    }
                val speed = speedKmph / KILOMETERS_IN_MILE
                speedLimitWidget?.update(speedLimit, speed.roundToInt(), signFormat, threshold)
            }
            UnitType.METRIC -> {
                val speedLimit = locationMatcherResult.speedLimit?.speedKmph
                speedLimitWidget?.update(speedLimit, speedKmph.roundToInt(), signFormat, threshold)
            }
        }
    }

    override fun onAttached(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("CarSpeedLimitRenderer carMapSurface loaded")
        val signFormat = options.speedLimitOptions.value.forcedSignFormat
            ?: SpeedLimitSign.MUTCD
        val speedLimitWidget = services.speedLimitWidget(signFormat).also { speedLimitWidget = it }
        mapboxCarMapSurface.mapSurface.addWidget(speedLimitWidget)
        MapboxNavigationApp.registerObserver(navigationObserver)
        scope = MainScope()
        options.speedLimitOptions
            .onEach { speedLimitWidget.update(it.forcedSignFormat, it.warningThreshold) }
            .launchIn(scope)
    }

    override fun onDetached(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("CarSpeedLimitRenderer carMapSurface detached")
        MapboxNavigationApp.unregisterObserver(navigationObserver)
        speedLimitWidget?.let { mapboxCarMapSurface.mapSurface.removeWidget(it) }
        speedLimitWidget = null
        scope.cancel()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect, edgeInsets: EdgeInsets) {
        speedLimitWidget?.setTranslation(-edgeInsets.right.toFloat(), -edgeInsets.bottom.toFloat())
    }

    private companion object {
        private const val METERS_IN_KILOMETER = 1000.0
        private const val KILOMETERS_IN_MILE = 1.609
        private const val SECONDS_IN_HOUR = 3600
    }
}
