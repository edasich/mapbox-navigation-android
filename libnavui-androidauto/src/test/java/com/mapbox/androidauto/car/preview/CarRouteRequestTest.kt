package com.mapbox.androidauto.car.preview

import com.mapbox.androidauto.car.MapboxCarOptions
import com.mapbox.androidauto.navigation.location.CarAppLocation
import com.mapbox.androidauto.testing.CarAppTestRule
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.testing.LoggingFrontendTestRule
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class CarRouteRequestTest {

    @get:Rule
    val loggerRule = LoggingFrontendTestRule()

    @get:Rule
    val carAppTestRule = CarAppTestRule()

    private val routeOptionsSlot = CapturingSlot<RouteOptions>()
    private val routerCallbackSlot = CapturingSlot<NavigationRouterCallback>()
    private val options: MapboxCarOptions = mockk {
        every { routeOptionsInterceptor } returns mockk {
            every { intercept(any()) } answers { firstArg() }
        }
    }

    private val locationProvider = mockk<NavigationLocationProvider>()
    private var requestCount = 0L
    private val mapboxNavigation = mockk<MapboxNavigation> {
        every {
            requestRoutes(capture(routeOptionsSlot), capture(routerCallbackSlot))
        } returns requestCount++
        every { cancelRouteRequest(any()) } just Runs
        every { navigationOptions } returns mockk {
            every { applicationContext } returns mockk()
            every { distanceFormatterOptions } returns mockk {
                every { locale } returns Locale.US
                every { unitType } returns UnitType.METRIC
            }
        }
        every { getZLevel() } returns Z_LEVEL
    }

    @Before
    fun setup() {
        every { MapboxNavigationApp.getObserver(CarAppLocation::class) } returns mockk {
            every { navigationLocationProvider } returns locationProvider
        }
    }

    private val carRouteRequest = CarRoutePreviewRequest(options)

    @Test
    fun `onRoutesReady is called after successful request`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )

        val routes = listOf(mockk<NavigationRoute>())
        routerCallbackSlot.captured.onRoutesReady(routes, mockk())

        verify(exactly = 1) { callback.onRoutesReady(any(), any()) }
    }

    @Test
    fun `onUnknownCurrentLocation is called when current location is null`() {
        every { locationProvider.lastLocation } returns null
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )

        verify { callback.onUnknownCurrentLocation() }
    }

    @Test
    fun `onSearchResultLocationUnknown is called when search result coordinate is`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns null },
            callback
        )

        verify { callback.onDestinationLocationUnknown() }
    }

    @Test
    fun `onNoRoutesFound is called when route request is canceled`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )

        routerCallbackSlot.captured.onCanceled(mockk(), mockk())

        verify { callback.onNoRoutesFound() }
    }

    @Test
    fun `onNoRoutesFound is called when route request fails`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )

        routerCallbackSlot.captured.onFailure(mockk(), mockk())

        verify { callback.onNoRoutesFound() }
    }

    @Test
    fun `onNoRoutesFound is called when mapboxNavigation is not attached`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )
        carRouteRequest.onAttached(mapboxNavigation)

        verify { callback.onNoRoutesFound() }
    }

    @Test
    fun `should cancel previous route request`() {
        every {
            locationProvider.lastLocation
        } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback: CarRoutePreviewRequestCallback = mockk(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )
        carRouteRequest.request(
            mockk { every { coordinate } returns searchCoordinate },
            callback
        )

        verify(exactly = 1) { mapboxNavigation.cancelRouteRequest(0) }
    }

    @Test
    fun `z level is passed to route options`() {
        every { locationProvider.lastLocation } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback = mockk<CarRoutePreviewRequestCallback>(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(mockk { every { coordinate } returns searchCoordinate }, callback)

        assertEquals(listOf(Z_LEVEL, null), routeOptionsSlot.captured.layersList())
    }

    @Test
    fun `custom route options provided by interceptor are used for route request`() {
        val customRouteOptions = mockk<RouteOptions>()
        val customRouteOptionsBuilder = mockk<RouteOptions.Builder> {
            every { build() } returns customRouteOptions
        }
        every { options.routeOptionsInterceptor.intercept(any()) } returns customRouteOptionsBuilder
        every { locationProvider.lastLocation } returns mockk {
            every { longitude } returns -121.4670161
            every { latitude } returns 38.5630514
        }
        val callback = mockk<CarRoutePreviewRequestCallback>(relaxUnitFun = true)
        val searchCoordinate = Point.fromLngLat(-121.467001, 38.568105)
        carRouteRequest.onAttached(mapboxNavigation)
        carRouteRequest.request(mockk { every { coordinate } returns searchCoordinate }, callback)

        assertEquals(customRouteOptions, routeOptionsSlot.captured)
    }

    private companion object {

        private const val Z_LEVEL = 42
    }
}
