package com.mapbox.navigation.qa_test_app.view

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.qa_test_app.databinding.InactiveRouteActivityLayoutBinding
import com.mapbox.navigation.qa_test_app.utils.Utils
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineScaleValue

class InactiveRouteStylingActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "InactiveRouteStylingAct"
    }

    private val binding: InactiveRouteActivityLayoutBinding by lazy {
        InactiveRouteActivityLayoutBinding.inflate(layoutInflater)
    }

    private val navigationLocationProvider = NavigationLocationProvider()
    private val replayRouteMapper = ReplayRouteMapper()
    private val mapboxReplayer = MapboxReplayer()
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)

    private val routeLineColorResources by lazy {
        RouteLineColorResources.Builder()
            .inActiveRouteLegsColor(Color.YELLOW)
            .inactiveRouteLegCasingColor(Color.GRAY)
            .restrictedRoadColor(Color.MAGENTA)
            .routeLineTraveledCasingColor(Color.RED)
            .routeCasingColor(Color.GREEN)
            .routeLineTraveledColor(Color.BLUE)
            .routeClosureColor(Color.BLACK)
            .routeDefaultColor(Color.LTGRAY)
            .build()
    }

    private val routeTrafficLineScaleExpression: Expression by lazy {
        buildScalingExpression(
            listOf(
                RouteLineScaleValue(4f, 3f, 1.5f),
                RouteLineScaleValue(10f, 4f, 1.5f),
                RouteLineScaleValue(13f, 6f, 1.5f),
                RouteLineScaleValue(16f, 10f, 1.5f),
                RouteLineScaleValue(19f, 14f, 1.5f),
                RouteLineScaleValue(22f, 18f, 1.5f)
            )
        )
    }

    private val alternativeRouteTrafficLineScaleExpression: Expression by lazy {
        buildScalingExpression(
            listOf(
                RouteLineScaleValue(4f, 3f, .5f),
                RouteLineScaleValue(10f, 4f, .5f),
                RouteLineScaleValue(13f, 6f, .5f),
                RouteLineScaleValue(16f, 10f, .5f),
                RouteLineScaleValue(19f, 14f, .5f),
                RouteLineScaleValue(22f, 18f, .5f)
            )
        )
    }

    private val routeLineResources: RouteLineResources by lazy {
        RouteLineResources.Builder()
            .routeLineColorResources(routeLineColorResources)
            .routeLineScaleExpression(routeTrafficLineScaleExpression)
            .routeTrafficLineScaleExpression(routeTrafficLineScaleExpression)
            .alternativeRouteLineScaleExpression(alternativeRouteTrafficLineScaleExpression)
            .alternativeRouteTrafficLineScaleExpression(alternativeRouteTrafficLineScaleExpression)
            .build()
    }

    private fun buildScalingExpression(scalingValues: List<RouteLineScaleValue>): Expression {
        val expressionBuilder = Expression.ExpressionBuilder("interpolate")
        expressionBuilder.addArgument(Expression.exponential { literal(1.5) })
        expressionBuilder.zoom()
        scalingValues.forEach { routeLineScaleValue ->
            expressionBuilder.stop {
                this.literal(routeLineScaleValue.scaleStop.toDouble())
                product {
                    literal(routeLineScaleValue.scaleMultiplier.toDouble())
                    literal(routeLineScaleValue.scale.toDouble())
                }
            }
        }
        return expressionBuilder.build()
    }

    private val onPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        val result = routeLineApi.updateTraveledRouteLine(point)
        binding.mapView.getMapboxMap().getStyle()?.apply {
            routeLineView.renderRouteLineUpdate(this, result)
        }
    }

    private val options: MapboxRouteLineOptions by lazy {
        MapboxRouteLineOptions.Builder(this)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label-navigation")
            .styleInactiveRouteLegsIndependently(true)
            .displayRestrictedRoadSections(true)
            .withVanishingRouteLineEnabled(true)
            .build()
    }

    private val routeLineView by lazy {
        MapboxRouteLineView(options)
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(options)
    }

    private val routeOrigin = Point.fromLngLat(-122.523179, 37.974972)

    private var routesObserver: RoutesObserver? = null

    private val mapboxNavigation by requireMapboxNavigation(
        onCreatedObserver = object : MapboxNavigationObserver {
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                initRoutesObserver(mapboxNavigation)
                mapboxNavigation.registerRoutesObserver(routesObserver!!)
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterRoutesObserver(routesObserver!!)
                routesObserver = null
                mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
                mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.unregisterLocationObserver(locationObserver)
                mapboxNavigation.stopTripSession()
            }
        }
    ) {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .accessToken(Utils.getMapboxAccessToken(this))
                .locationEngine(ReplayLocationEngine(mapboxReplayer))
                .build()
        )
    }

    private fun initRoutesObserver(mapboxNavigation: MapboxNavigation) {
        routesObserver = RoutesObserver {
            routeLineApi.setNavigationRoutes(
                it.navigationRoutes,
                mapboxNavigation.currentLegIndex()
            ) {
                binding.mapView.getMapboxMap().getStyle()?.let { style ->
                    routeLineView.renderRouteDrawData(style, it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initStyle()
        initListeners()
    }

    @SuppressLint("MissingPermission")
    private fun initListeners() {
        val startingLocation = Location("ReplayRoute").also {
            it.latitude = routeOrigin.latitude()
            it.longitude = routeOrigin.longitude()
        }
        navigationLocationProvider.changePosition(startingLocation)
        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            addOnIndicatorPositionChangedListener(onPositionChangedListener)
            enabled = true
        }
        mapboxReplayer.pushRealLocation(this, 0.0)
        mapboxReplayer.playbackSpeed(1.0)
        mapboxReplayer.play()
        binding.startNavigation.setOnClickListener {
            mapboxNavigation.setRerouteController(null)
            mapboxNavigation.startTripSession()
            mapboxNavigation.requestRoutes(
                RouteOptions.builder()
                    .applyDefaultNavigationOptions(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
                    .coordinates(
                        "-122.523179,37.974972;-122.524257,37.970785;-122.518925,37.970548"
                    )
                    .annotations("congestion,maxspeed,speed,duration,distance,closure")
                    .build(),
                object : NavigationRouterCallback {
                    override fun onRoutesReady(
                        routes: List<NavigationRoute>,
                        routerOrigin: RouterOrigin
                    ) {
                        mapboxNavigation.setNavigationRoutes(routes)
                        startSimulation(routes.first().directionsRoute)
                    }

                    override fun onFailure(
                        reasons: List<RouterFailure>,
                        routeOptions: RouteOptions
                    ) {
                    }

                    override fun onCanceled(
                        routeOptions: RouteOptions,
                        routerOrigin: RouterOrigin
                    ) {
                    }
                }
            )
            binding.startNavigation.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        routeLineApi.cancel()
        routeLineView.cancel()
        mapboxReplayer.finish()
    }

    private fun initStyle() {
        binding.mapView.getMapboxMap().loadStyleUri(
            NavigationStyles.NAVIGATION_DAY_STYLE
        ) {
            val cameraOptions = CameraOptions.Builder().center(routeOrigin).zoom(14.0).build()
            binding.mapView.getMapboxMap().setCamera(cameraOptions)
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {
            Log.d(TAG, "raw location $rawLocation")
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            if (locationMatcherResult.enhancedLocation.provider == "ReplayRoute") {
                navigationLocationProvider.changePosition(
                    locationMatcherResult.enhancedLocation,
                    locationMatcherResult.keyPoints,
                )
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->

        // This is the most important part of this example. The route progress will be used to
        // determine the active leg and adjust the route line visibility accordingly.
        routeLineApi.updateWithRouteProgress(routeProgress) { result ->
            binding.mapView.getMapboxMap().getStyle()?.apply {
                routeLineView.renderRouteLineUpdate(this, result)
            }
        }
    }

    private fun startSimulation(route: DirectionsRoute) {
        mapboxReplayer.stop()
        mapboxReplayer.clearEvents()
        val replayData = replayRouteMapper.mapDirectionsRouteGeometry(route)
        mapboxReplayer.pushEvents(replayData)
        mapboxReplayer.seekTo(replayData[0])
        mapboxReplayer.play()
    }
}
