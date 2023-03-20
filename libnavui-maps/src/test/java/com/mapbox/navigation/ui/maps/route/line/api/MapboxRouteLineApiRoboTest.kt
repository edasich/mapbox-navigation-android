package com.mapbox.navigation.ui.maps.route.line.api

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.bindgen.Expected
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.LineString
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.route.toNavigationRoute
import com.mapbox.navigation.base.trip.model.RouteProgressState
import com.mapbox.navigation.core.routealternatives.AlternativeRouteMetadata
import com.mapbox.navigation.testing.FileUtils
import com.mapbox.navigation.testing.LoggingFrontendTestRule
import com.mapbox.navigation.testing.MainCoroutineRule
import com.mapbox.navigation.testing.NativeRouteParserRule
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineUtils
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.getRouteDrawData
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.setNavigationRoutes
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.setRoutes
import com.mapbox.navigation.ui.maps.route.line.MapboxRouteLineApiExtensions.showRouteWithLegIndexHighlighted
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLine
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineDistancesIndex
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineError
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineGranularDistances
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteSetValue
import com.mapbox.navigation.ui.maps.testing.TestRoute
import com.mapbox.navigation.ui.maps.testing.TestingUtil.loadNavigationRoute
import com.mapbox.navigation.ui.maps.testing.TestingUtil.loadRoute
import com.mapbox.navigation.utils.internal.InternalJobControlFactory
import com.mapbox.navigation.utils.internal.JobControl
import com.mapbox.navigation.utils.internal.LoggerFrontend
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MapboxRouteLineApiRoboTest {

    lateinit var ctx: Context

    @get:Rule
    var coroutineRule = MainCoroutineRule()

    private val logger = mockk<LoggerFrontend>(relaxed = true)

    @get:Rule
    val loggerRule = LoggingFrontendTestRule(logger)

    @get:Rule
    val nativeRouteParserRule = NativeRouteParserRule()

    private val parentJob = SupervisorJob()
    private val testScope = CoroutineScope(parentJob + coroutineRule.testDispatcher)

    private val shortRoute by lazy { TestRoute(fileName = "short_route.json") }
    private val routeWithRestrictions by lazy {
        TestRoute(fileName = "route-with-restrictions.json")
    }
    private val multiLegRouteTwoLegs by lazy {
        TestRoute(fileName = "multileg-route-two-legs.json")
    }
    private val multiLegRouteWithOverlap by lazy {
        TestRoute(fileName = "multileg_route_with_overlap.json")
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        mockkObject(InternalJobControlFactory)
        every {
            InternalJobControlFactory.createDefaultScopeJobControl()
        } returns JobControl(parentJob, testScope)
    }

    @After
    fun cleanUp() {
        unmockkObject(InternalJobControlFactory)
    }

    @Test
    fun setRoutes() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx).build()
        val api = MapboxRouteLineApi(options)
        val expectedCasingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]")
        )
        val expectedRouteLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedTrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.942549),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(1.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedPrimaryRouteSourceGeometry = "LineString{type=LineString, bbox=null, " +
            "coordinates=[Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}," +
            " Point{type=Point, bbox=null, coordinates=[-122.523729, 37.975194]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523579, 37.975173]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523117, 37.975107]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}]}"
        val expectedWaypointFeature0 =
            "Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}"
        val expectedWaypointFeature1 =
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}"
        val expectedMaskingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(1.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
        )

        val route = loadRoute("short_route.json")
        val routes = listOf(RouteLine(route, null))

        val result = api.setRoutes(routes)

        checkExpression(
            expectedCasingExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.casingExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedRouteLineExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.baseExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedTrafficLineExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        assertEquals(
            expectedPrimaryRouteSourceGeometry,
            result.value!!.primaryRouteLineData.featureCollection.features()!![0].geometry()
                .toString()
        )
        assertTrue(
            result.value!!.alternativeRouteLinesData[0].featureCollection.features()!!.isEmpty()
        )
        assertTrue(
            result.value!!.alternativeRouteLinesData[1].featureCollection.features()!!.isEmpty()
        )
        assertEquals(
            expectedWaypointFeature0,
            result.value!!.waypointsSource.features()!![0].geometry().toString()
        )
        assertEquals(
            expectedWaypointFeature1,
            result.value!!.waypointsSource.features()!![1].geometry().toString()
        )
        checkExpression(
            expectedMaskingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trafficExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedMaskingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.baseExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedMaskingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.casingExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedMaskingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trailExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedMaskingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trailCasingExpressionProvider!!
                .generateExpression()
        )
    }

    @Test
    fun setRoutes_maskingLayerExpressionsWithMultiLegRoute() = coroutineRule.runBlockingTest {
        val colors = RouteLineColorResources.Builder()
            .routeLowCongestionColor(Color.GRAY)
            .routeUnknownCongestionColor(Color.LTGRAY)
            .routeDefaultColor(Color.BLUE)
            .routeLineTraveledColor(Color.RED)
            .routeLineTraveledCasingColor(Color.MAGENTA)
            .inActiveRouteLegsColor(Color.YELLOW)
            .build()
        val resources = RouteLineResources.Builder().routeLineColorResources(colors).build()
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withRouteLineResources(resources)
            .withVanishingRouteLineEnabled(true)
            .styleInactiveRouteLegsIndependently(true)
            .build()
        val api = MapboxRouteLineApi(options)

        val expectedMaskingTrafficExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 136.0, 136.0, 136.0, 1.0]"),
            DoubleChecker(0.4822003),
            StringChecker("[rgba, 204.0, 204.0, 204.0, 1.0]"),
            DoubleChecker(0.4848101),
            StringChecker("[rgba, 136.0, 136.0, 136.0, 1.0]"),
            DoubleChecker(0.490409),
            StringChecker("[rgba, 204.0, 204.0, 204.0, 1.0]"),
            DoubleChecker(0.4922238),
            StringChecker("[rgba, 136.0, 136.0, 136.0, 1.0]"),
            DoubleChecker(0.4978029),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )
        val expectedMaskingBaseExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 255.0, 1.0]"),
            DoubleChecker(0.4978029),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )
        val expectedMaskingCasingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
            DoubleChecker(0.4978029),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )
        val expectedMaskingTrailExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 255.0, 0.0, 0.0, 1.0]"),
            DoubleChecker(0.4978029),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )
        val expectedMaskingTrailCasingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 255.0, 0.0, 255.0, 1.0]"),
            DoubleChecker(0.4978029),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )

        val result = api.setNavigationRoutes(listOf(multiLegRouteWithOverlap.navigationRoute))

        checkExpression(
            expectedMaskingTrafficExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trafficExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedMaskingBaseExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.baseExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedMaskingCasingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.casingExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedMaskingTrailExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trailExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedMaskingTrailCasingExpressionContents,
            result.value!!.routeLineMaskingLayerDynamicData!!.trailCasingExpressionProvider!!
                .generateExpression()
        )
    }

    @Test
    fun setRoutesWithCallback() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx).build()
        val api = MapboxRouteLineApi(options)
        val expectedCasingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]")
        )
        val expectedRouteLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedTrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.942549),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(1.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedPrimaryRouteSourceGeometry = "LineString{type=LineString, bbox=null, " +
            "coordinates=[Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}," +
            " Point{type=Point, bbox=null, coordinates=[-122.523729, 37.975194]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523579, 37.975173]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523117, 37.975107]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}]}"
        val expectedWaypointFeature0 =
            "Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}"
        val expectedWaypointFeature1 =
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}"
        val route = loadRoute("short_route.json")
        val routes = listOf(RouteLine(route, null))

        var result: RouteSetValue? = null
        val consumer = MapboxNavigationConsumer<Expected<RouteLineError, RouteSetValue>> {
            result = it.value
        }
        api.setRoutes(routes, consumer)

        checkExpression(
            expectedCasingExpressionContents,
            result!!.primaryRouteLineData.dynamicData.casingExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedRouteLineExpressionContents,
            result!!.primaryRouteLineData.dynamicData.baseExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedTrafficLineExpressionContents,
            result!!.primaryRouteLineData.dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        assertEquals(
            expectedPrimaryRouteSourceGeometry,
            result!!.primaryRouteLineData.featureCollection.features()!![0].geometry()
                .toString()
        )
        assertTrue(
            result!!.alternativeRouteLinesData[0].featureCollection.features()!!.isEmpty()
        )
        assertTrue(
            result!!.alternativeRouteLinesData[1].featureCollection.features()!!.isEmpty()
        )
        assertEquals(
            expectedWaypointFeature0,
            result!!.waypointsSource.features()!![0].geometry().toString()
        )
        assertEquals(
            expectedWaypointFeature1,
            result!!.waypointsSource.features()!![1].geometry().toString()
        )
    }

    @Test
    fun `setRoutes with restrictions across legs`() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .displayRestrictedRoadSections(true)
            .withRouteLineResources(
                RouteLineResources.Builder()
                    .routeLineColorResources(
                        RouteLineColorResources.Builder()
                            .restrictedRoadColor(Color.CYAN)
                            .build()
                    )
                    .build()
            )
            .build()
        val api = MapboxRouteLineApi(options)
        val expectedRestrictedExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.39564579),
            StringChecker("[rgba, 0.0, 255.0, 255.0, 1.0]"),
            DoubleChecker(0.554003),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
        )
        val route = loadRoute("multileg_route_two_legs_with_restrictions.json")
            .toNavigationRoute(RouterOrigin.Offboard)

        val result = api.setNavigationRoutes(listOf(route))

        checkExpression(
            expectedRestrictedExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.restrictedSectionExpressionProvider!!
                .generateExpression()
        )
    }

    @Test
    fun setRoutesTrafficExpressionsWithAlternativeRoutes() = coroutineRule.runBlockingTest {
        val expectedPrimaryTrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.94254989),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(1.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedAlternative1TrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.42786239),
            StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
            DoubleChecker(0.4958641),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]")
        )
        val expectedAlternative2TrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.09121734),
            StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
            DoubleChecker(0.0996932),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.742905),
            StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
            DoubleChecker(0.7533370),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.7911074),
            StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
            DoubleChecker(0.8172277),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.8647541),
            StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
            DoubleChecker(0.8661804),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            DoubleChecker(0.8880790),
            StringChecker("[rgba, 181.0, 130.0, 129.0, 1.0]"),
            DoubleChecker(0.92755122),
            StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]")
        )
        val options = MapboxRouteLineOptions.Builder(ctx).build()
        val api = MapboxRouteLineApi(options)
        val route = loadRoute("short_route.json")
        val altRoute1 = loadRoute("route-with-road-classes.txt")
        val altRoute2 = loadRoute("multileg_route.json")
        val routes = listOf(
            RouteLine(route, null),
            RouteLine(altRoute1, null),
            RouteLine(altRoute2, null)
        )

        val result = api.setRoutes(routes)

        checkExpression(
            expectedPrimaryTrafficLineExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedAlternative1TrafficLineExpressionContents,
            result.value!!.alternativeRouteLinesData[0].dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedAlternative2TrafficLineExpressionContents,
            result.value!!.alternativeRouteLinesData[1].dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
    }

    @Test
    fun getRouteDrawData() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx).build()
        val api = MapboxRouteLineApi(options)
        val expectedCasingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]")
        )
        val expectedRouteLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedTrafficLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.9425498),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(1.0),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedPrimaryRouteSourceGeometry = "LineString{type=LineString, bbox=null, " +
            "coordinates=[Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}," +
            " Point{type=Point, bbox=null, coordinates=[-122.523729, 37.975194]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523579, 37.975173]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523117, 37.975107]}, " +
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}]}"
        val expectedWaypointFeature0 =
            "Point{type=Point, bbox=null, coordinates=[-122.523671, 37.975379]}"
        val expectedWaypointFeature1 =
            "Point{type=Point, bbox=null, coordinates=[-122.523131, 37.975067]}"
        val route = loadRoute("short_route.json")
        val routes = listOf(RouteLine(route, null))
        api.setRoutes(routes)

        val result = api.getRouteDrawData()

        checkExpression(
            expectedCasingExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.casingExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedRouteLineExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.baseExpressionProvider
                .generateExpression()
        )
        checkExpression(
            expectedTrafficLineExpressionContents,
            result.value!!.primaryRouteLineData.dynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        assertEquals(
            expectedPrimaryRouteSourceGeometry,
            result.value!!.primaryRouteLineData.featureCollection.features()!![0].geometry()
                .toString()
        )
        assertTrue(
            result.value!!.alternativeRouteLinesData[0].featureCollection.features()!!.isEmpty()
        )
        assertTrue(
            result.value!!.alternativeRouteLinesData[1].featureCollection.features()!!.isEmpty()
        )
        assertEquals(
            expectedWaypointFeature0,
            result.value!!.waypointsSource.features()!![0].geometry().toString()
        )
        assertEquals(
            expectedWaypointFeature1,
            result.value!!.waypointsSource.features()!![1].geometry().toString()
        )
    }

    @Test
    fun updateTraveledRouteLine() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withVanishingRouteLineEnabled(true)
            .displayRestrictedRoadSections(false)
            .vanishingRouteLineUpdateInterval(0)
            .build()
        val api = MapboxRouteLineApi(options)
        val expectedCasingExpressionContents = listOf(
            StringChecker("literal"),
            ListChecker(DoubleChecker(0.0), DoubleChecker(0.3240769))
        )
        val expectedRouteExpressionContents = listOf(
            StringChecker("literal"),
            ListChecker(DoubleChecker(0.0), DoubleChecker(0.32407694))
        )
        val expectedTrafficExpressionContents = listOf(
            StringChecker("literal"),
            ListChecker(DoubleChecker(0.0), DoubleChecker(0.324076944))
        )
        val expectedRestrictedExpressionContents = listOf(
            StringChecker("literal"),
            ListChecker(DoubleChecker(0.0), DoubleChecker(0.32407694))
        )
        val route = shortRoute.navigationRoute
        val lineString = LineString.fromPolyline(
            route.directionsRoute.geometry() ?: "",
            Constants.PRECISION_6
        )
        val routeProgress = shortRoute.mockRouteProgress(stepIndexValue = 2)

        api.updateVanishingPointState(RouteProgressState.TRACKING)
        api.setNavigationRoutes(listOf(route))
        api.updateUpcomingRoutePointIndex(routeProgress)

        val result = api.updateTraveledRouteLine(lineString.coordinates()[1])

        checkExpression(
            expectedCasingExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .casingExpressionProvider.generateExpression()
        )
        checkExpression(
            expectedRouteExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            expectedTrafficExpressionContents,
            result.value!!
                .primaryRouteLineDynamicData.trafficExpressionProvider!!
                .generateExpression()
        )
        checkExpression(
            expectedRestrictedExpressionContents,
            result.value!!
                .primaryRouteLineDynamicData.restrictedSectionExpressionProvider!!
                .generateExpression()
        )
    }

    @Test
    fun updateTraveledRouteLine_pointUpdateIntervalRespected() =
        coroutineRule.runBlockingTest {
            val options = MapboxRouteLineOptions.Builder(ctx)
                .withVanishingRouteLineEnabled(true)
                .displayRestrictedRoadSections(false)
                .vanishingRouteLineUpdateInterval(TimeUnit.MILLISECONDS.toNanos(1200))
                .build()
            val api = MapboxRouteLineApi(options)
            val route = shortRoute.navigationRoute
            val lineString = LineString.fromPolyline(
                route.directionsRoute.geometry() ?: "",
                Constants.PRECISION_6
            )
            val routeProgress = shortRoute.mockRouteProgress(stepIndexValue = 2)

            api.updateVanishingPointState(RouteProgressState.TRACKING)
            api.setNavigationRoutes(listOf(route))
            api.updateUpcomingRoutePointIndex(routeProgress)

            pauseDispatcher {
                val result1 = api.updateTraveledRouteLine(lineString.coordinates()[1])
                assertTrue(result1.isValue)

                Thread.sleep(1000L)
                api.updateUpcomingRoutePointIndex(routeProgress) // only update the progress
                Thread.sleep(300L) // in summary we've waited for 1.3s since last point update
                val result2 = api.updateTraveledRouteLine(lineString.coordinates()[2])
                assertTrue(result2.isValue) // should succeed because threshold was 1.2s

                Thread.sleep(500L) // wait less than threshold
                val result3 = api.updateTraveledRouteLine(lineString.coordinates()[1])
                assertTrue(result3.isError)
            }
        }

    @Test
    fun updateTraveledRouteLine_noUpdateWhenPointDistanceTooSmall() =
        coroutineRule.runBlockingTest {
            val options = MapboxRouteLineOptions.Builder(ctx)
                .withVanishingRouteLineEnabled(true)
                .displayRestrictedRoadSections(false)
                .vanishingRouteLineUpdateInterval(TimeUnit.MILLISECONDS.toNanos(1200))
                .build()
            val api = MapboxRouteLineApi(options)
            val route = shortRoute.navigationRoute
            val lineString = LineString.fromPolyline(
                route.directionsRoute.geometry() ?: "",
                Constants.PRECISION_6
            )
            val routeProgress = shortRoute.mockRouteProgress(stepIndexValue = 2)

            api.updateVanishingPointState(RouteProgressState.TRACKING)
            api.setNavigationRoutes(listOf(route))
            api.updateUpcomingRoutePointIndex(routeProgress)

            pauseDispatcher {
                val result1 = api.updateTraveledRouteLine(lineString.coordinates()[1])
                assertTrue(result1.isValue)

                Thread.sleep(1000L)
                api.updateUpcomingRoutePointIndex(routeProgress)
                Thread.sleep(300L)
                val result2 = api.updateTraveledRouteLine(lineString.coordinates()[1])
                assertTrue(result2.isError)
            }
        }

    @Test
    fun `updateTraveledRouteLine when route has restrictions and legs not styled independently`() =
        coroutineRule.runBlockingTest {
            val options = MapboxRouteLineOptions.Builder(ctx)
                .withVanishingRouteLineEnabled(true)
                .displayRestrictedRoadSections(true)
                .vanishingRouteLineUpdateInterval(0)
                .build()
            val api = MapboxRouteLineApi(options)
            val expectedRestrictedExpressionContents = listOf(
                StringChecker("literal"),
                ListChecker(DoubleChecker(0.0), DoubleChecker(0.04111412))
            )
            val route = routeWithRestrictions.navigationRoute
            val lineString = LineString.fromPolyline(
                route.directionsRoute.geometry() ?: "",
                Constants.PRECISION_6
            )
            val routeProgress = routeWithRestrictions.mockRouteProgress(stepIndexValue = 2)

            api.updateVanishingPointState(RouteProgressState.TRACKING)
            api.setNavigationRoutes(listOf(route))
            api.updateUpcomingRoutePointIndex(routeProgress)

            mockkObject(MapboxRouteLineUtils)
            val result = api.updateTraveledRouteLine(lineString.coordinates()[1])

            checkExpression(
                expectedRestrictedExpressionContents,
                result.value!!.primaryRouteLineDynamicData.restrictedSectionExpressionProvider!!
                    .generateExpression()
            )

            verify(exactly = 0) {
                // the cache key is based on the full hash of the Directions Route
                // and is not suited to be used as frequently as the vanishing route line needs it
                MapboxRouteLineUtils.extractRouteData(any(), any())
            }
            unmockkObject(MapboxRouteLineUtils)
        }

    @Test
    fun updateTraveledRouteLine_whenRouteRestrictionsEnabledButHasNone() =
        coroutineRule.runBlockingTest {
            val expectedCasingExpressionContents = listOf(
                StringChecker("literal"),
                ListChecker(DoubleChecker(0.0), DoubleChecker(0.3240769))
            )
            val expectedRouteExpressionContents = listOf(
                StringChecker("literal"),
                ListChecker(DoubleChecker(0.0), DoubleChecker(0.3240769))
            )
            val expectedTrafficExpressionContents = listOf(
                StringChecker("literal"),
                ListChecker(DoubleChecker(0.0), DoubleChecker(0.3240769))
            )
            val restrictedTrafficExpressionContents = listOf(
                StringChecker("literal"),
                ListChecker(DoubleChecker(0.0), DoubleChecker(0.3240769))
            )
            val options = MapboxRouteLineOptions.Builder(ctx)
                .withVanishingRouteLineEnabled(true)
                .displayRestrictedRoadSections(true)
                .vanishingRouteLineUpdateInterval(0)
                .build()
            val api = MapboxRouteLineApi(options)
            val route = shortRoute.navigationRoute
            val lineString = LineString.fromPolyline(
                route.directionsRoute.geometry() ?: "",
                Constants.PRECISION_6
            )
            val routeProgress = shortRoute.mockRouteProgress(stepIndexValue = 2)

            api.updateVanishingPointState(RouteProgressState.TRACKING)
            api.setNavigationRoutes(listOf(route))
            api.updateUpcomingRoutePointIndex(routeProgress)

            val result = api.updateTraveledRouteLine(lineString.coordinates()[1])

            checkExpression(
                expectedCasingExpressionContents,
                result.value!!.primaryRouteLineDynamicData
                    .casingExpressionProvider.generateExpression()
            )
            checkExpression(
                expectedRouteExpressionContents,
                result.value!!.primaryRouteLineDynamicData
                    .baseExpressionProvider.generateExpression()
            )
            checkExpression(
                expectedTrafficExpressionContents,
                result.value!!
                    .primaryRouteLineDynamicData.trafficExpressionProvider!!
                    .generateExpression()
            )
            checkExpression(
                restrictedTrafficExpressionContents,
                result.value!!
                    .primaryRouteLineDynamicData.restrictedSectionExpressionProvider!!
                    .generateExpression()
            )
        }

    @Test
    fun updateWithRouteProgress_whenDeEmphasizeInactiveLegSegments() =
        coroutineRule.runBlockingTest {
            val expectedTrafficExpContents = listOf(
                StringChecker("step"),
                StringChecker("[line-progress]"),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
                DoubleChecker(0.0),
                StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
                DoubleChecker(0.1037382),
                StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
                DoubleChecker(0.124012),
                StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
                DoubleChecker(0.2718982),
                StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
                DoubleChecker(0.3226409),
                StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
                DoubleChecker(0.4897719),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]")
            )
            val realOptions = MapboxRouteLineOptions.Builder(ctx)
                .styleInactiveRouteLegsIndependently(true)
                .build()
            val route = multiLegRouteTwoLegs.navigationRoute
            val mockVanishingRouteLine = mockk<VanishingRouteLine>(relaxUnitFun = true) {
                every { vanishPointOffset } returns 0.0
            }
            val options = mockk<MapboxRouteLineOptions> {
                every { vanishingRouteLine } returns mockVanishingRouteLine
                every { resourceProvider } returns realOptions.resourceProvider
                every {
                    styleInactiveRouteLegsIndependently
                } returns realOptions.styleInactiveRouteLegsIndependently
                every { displayRestrictedRoadSections } returns false
                every { displaySoftGradientForTraffic } returns false
                every { softGradientTransition } returns 30.0
                every { routeStyleDescriptors } returns listOf()
            }
            val api = MapboxRouteLineApi(options)
            val routeProgress = multiLegRouteTwoLegs.mockRouteProgress()
            api.updateVanishingPointState(RouteProgressState.TRACKING)
            api.setNavigationRoutes(listOf(route))
            api.updateWithRouteProgress(routeProgress) {}

            val result = api.setVanishingOffset(0.0).value!!

            checkExpression(
                expectedTrafficExpContents,
                result.primaryRouteLineDynamicData
                    .trafficExpressionProvider!!.generateExpression()
            )
        }

    @Test
    fun highlightActiveLeg() = coroutineRule.runBlockingTest {
        var callbackCalled = false
        val expectedTrafficExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.54213882),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.5710651),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.5916095),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.8867442),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.94230022),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedRouteLineExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val expectedCasingExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
        )
        val route = multiLegRouteTwoLegs.navigationRoute
        val options = MapboxRouteLineOptions.Builder(ctx)
            .styleInactiveRouteLegsIndependently(true)
            .build()
        val api = MapboxRouteLineApi(options)
        val routeProgress = multiLegRouteTwoLegs.mockRouteProgress(legIndexValue = 1)
        api.setNavigationRoutes(listOf(route))

        api.updateWithRouteProgress(routeProgress) { result ->
            checkExpression(
                expectedTrafficExpContents,
                result.value!!.primaryRouteLineDynamicData
                    .trafficExpressionProvider!!.generateExpression()
            )
            checkExpression(
                expectedRouteLineExpContents,
                result.value!!
                    .primaryRouteLineDynamicData.baseExpressionProvider
                    .generateExpression()
            )
            checkExpression(
                expectedCasingExpContents,
                result.value!!.primaryRouteLineDynamicData
                    .casingExpressionProvider.generateExpression()
            )
            callbackCalled = true
        }
        assertTrue(callbackCalled)
    }

    @Test
    fun showRouteWithLegIndexHighlighted() = coroutineRule.runBlockingTest {
        val expectedTrafficExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.54213882),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.5710651),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.5916095),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.8867442),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.94230022),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val expectedRouteLineExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val expectedCasingExpContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
            DoubleChecker(0.0),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.48977199),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
        )
        val route = loadRoute("multileg-route-two-legs.json")
        val options = MapboxRouteLineOptions.Builder(ctx)
            .styleInactiveRouteLegsIndependently(true)
            .build()
        val api = MapboxRouteLineApi(options)
        api.setRoutes(listOf(RouteLine(route, null)))

        val result = api.showRouteWithLegIndexHighlighted(1).value!!

        checkExpression(
            expectedTrafficExpContents,
            result.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression()
        )
        checkExpression(
            expectedRouteLineExpContents,
            result.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            expectedCasingExpContents,
            result.primaryRouteLineDynamicData.casingExpressionProvider
                .generateExpression()
        )
    }

    @Test
    fun setVanishingOffset() {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withVanishingRouteLineEnabled(true)
            .build()
        val trafficExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val routeLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val casingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]")
        )
        val api = MapboxRouteLineApi(
            options
        )

        val result = api.setVanishingOffset(.5)

        checkExpression(
            trafficExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression()
        )
        checkExpression(
            routeLineExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            casingExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .casingExpressionProvider.generateExpression()
        )
    }

    @Test
    fun setVanishingOffset_withRestrictionsEnabledPrimaryRouteNull() {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withVanishingRouteLineEnabled(true)
            .displayRestrictedRoadSections(true)
            .build()
        val trafficExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val routeLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]")
        )
        val casingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]")
        )

        val api = MapboxRouteLineApi(
            options
        )

        val result = api.setVanishingOffset(.5)

        checkExpression(
            trafficExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression()
        )
        checkExpression(
            routeLineExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            casingExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .casingExpressionProvider.generateExpression()
        )
        assertNull(result.value!!.primaryRouteLineDynamicData.restrictedSectionExpressionProvider)
    }

    @Test
    fun setVanishingOffset_withRestrictionsEnabled() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withVanishingRouteLineEnabled(true)
            .displayRestrictedRoadSections(true)
            .build()
        val trafficExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.502164),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.8591201),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.8902258),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val routeLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val casingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
        )
        val restrictedExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.50216434),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 1.0]"),
            DoubleChecker(0.51964451),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
        )
        val route = loadRoute("route-with-restrictions.json")

        val api = MapboxRouteLineApi(
            options
        )
        api.setRoutes(listOf(RouteLine(route, null)))

        val result = api.setVanishingOffset(.5)

        println(
            result.value!!.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression().toString()
        )
        checkExpression(
            trafficExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression()
        )
        checkExpression(
            routeLineExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            casingExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .casingExpressionProvider.generateExpression()
        )
        checkExpression(
            restrictedExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .restrictedSectionExpressionProvider!!.generateExpression()
        )
    }

    @Test
    fun setVanishingOffset_whenHasRestrictionsButDisabled() = coroutineRule.runBlockingTest {
        val options = MapboxRouteLineOptions.Builder(ctx)
            .withVanishingRouteLineEnabled(true)
            .displayRestrictedRoadSections(false)
            .build()
        val trafficExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.502164),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            DoubleChecker(0.8591201),
            StringChecker("[rgba, 255.0, 149.0, 0.0, 1.0]"),
            DoubleChecker(0.8902258),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val routeLineExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
        )
        val casingExpressionContents = listOf(
            StringChecker("step"),
            StringChecker("[line-progress]"),
            StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
            DoubleChecker(0.5),
            StringChecker("[rgba, 47.0, 122.0, 198.0, 1.0]"),
        )
        val route = loadRoute("route-with-restrictions.json")

        val api = MapboxRouteLineApi(
            options
        )
        api.setRoutes(listOf(RouteLine(route, null)))

        val result = api.setVanishingOffset(.5)

        checkExpression(
            trafficExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .trafficExpressionProvider!!.generateExpression()
        )
        checkExpression(
            routeLineExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .baseExpressionProvider.generateExpression()
        )
        checkExpression(
            casingExpressionContents,
            result.value!!.primaryRouteLineDynamicData
                .casingExpressionProvider.generateExpression()
        )
        assertNull(result.value!!.primaryRouteLineDynamicData.restrictedSectionExpressionProvider)
    }

    @Test
    fun setRouteAsyncCallsReturnsCorrectRouteSuspend() = coroutineRule.runBlockingTest {
        val shortRoute = listOf(RouteLine(loadRoute("short_route.json"), null))
        val longRoute = listOf(RouteLine(loadRoute("cross-country-route.json"), null))
        val options = MapboxRouteLineOptions.Builder(ctx).build()
        val api = MapboxRouteLineApi(options)

        val longRouteDef = async {
            val result = api.setRoutes(longRoute)
            (
                result
                    .value!!
                    .primaryRouteLineData
                    .dynamicData
                    .trafficExpressionProvider!!
                    .generateExpression()
                    .contents as ArrayList<*>
                ).size
        }
        delay(40)
        val shortRouteDef = async {
            val result = api.setRoutes(shortRoute)
            (
                result
                    .value!!
                    .primaryRouteLineData
                    .dynamicData
                    .trafficExpressionProvider!!
                    .generateExpression()
                    .contents as ArrayList<*>
                ).size
        }

        assertEquals(9, shortRouteDef.await())
        assertEquals(625, longRouteDef.await())
    }

    @Test
    fun alternativelyStyleSegmentsNotInLeg() = coroutineRule.runBlockingTest {
        val colorOptions = RouteLineColorResources.Builder()
            .inActiveRouteLegsColor(Color.YELLOW)
            .build()
        val resources = RouteLineResources.Builder().routeLineColorResources(colorOptions).build()
        val options = MapboxRouteLineOptions.Builder(ctx).withRouteLineResources(resources).build()
        val route = loadNavigationRoute("multileg-route-two-legs.json")
        val segments = MapboxRouteLineUtils.calculateRouteLineSegments(
            route,
            listOf(),
            true,
            options.resourceProvider.routeLineColorResources
        )
        val api = MapboxRouteLineApi(options)

        val result = api.alternativelyStyleSegmentsNotInLeg(1, segments, Color.YELLOW)

        assertEquals(12, result.size)
        assertEquals(-256, result.first().segmentColor)
        assertEquals(0, result.first().legIndex)
        assertEquals(-256, result[4].segmentColor)
        assertEquals(0, result[4].legIndex)
        // this will be ignored, it's only here because the test route has an incorrect geometry
        // with a duplicate point
        assertEquals(0.4897719974699625, result[5].offset, 0.0)
        assertEquals(-27392, result[6].segmentColor)
        assertEquals(1, result[6].legIndex)
        assertEquals(0.4897719974699625, result[6].offset, 0.0)
    }

    @Test
    fun `set routes - with alternative metadata - vanished until deviation point`() =
        coroutineRule.runBlockingTest {
            val options = MapboxRouteLineOptions.Builder(ctx).build()
            val api = MapboxRouteLineApi(options)
            val response = DirectionsResponse.fromJson(
                FileUtils.loadJsonFixture(
                    "route_response_alternative_start.json"
                )
            )
            val routeOptions = response.routes().first().routeOptions()!!
            val routes = NavigationRoute.create(
                directionsResponse = DirectionsResponse.fromJson(
                    FileUtils.loadJsonFixture(
                        "route_response_alternative_start.json"
                    )
                ),
                routeOptions = routeOptions
            )
            val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
                every { navigationRoute } returns routes[1]
                every { forkIntersectionOfAlternativeRoute } returns mockk {
                    every { location } returns mockk()
                    every { geometryIndexInRoute } returns 2
                    every { geometryIndexInLeg } returns 2
                    every { legIndex } returns 0
                }
                every { forkIntersectionOfPrimaryRoute } returns mockk {
                    every { location } returns mockk()
                    every { geometryIndexInRoute } returns 2
                    every { geometryIndexInLeg } returns 2
                    every { legIndex } returns 0
                }
                every { infoFromFork } returns mockk {
                    every { distance } returns 1588.7698034413877
                    every { duration } returns 372.0307335579433
                }
                every { infoFromStartOfPrimary } returns mockk {
                    every { distance } returns 1652.4918669972706
                    every { duration } returns 400.6847335579434
                }
            }

            val result = api.setNavigationRoutes(routes, listOf(alternativeRouteMetadata)).value!!
            val expectedPrimaryTrafficExpressionContents = listOf(
                StringChecker("step"),
                StringChecker("[line-progress]"),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
                DoubleChecker(0.0),
                StringChecker("[rgba, 86.0, 168.0, 251.0, 1.0]"),
            )
            val expectedBaseExpressionContents = listOf(
                StringChecker("step"),
                StringChecker("[line-progress]"),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
                DoubleChecker(0.03856129),
                StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            )
            val expectedCasingExpressionContents = listOf(
                StringChecker("step"),
                StringChecker("[line-progress]"),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
                DoubleChecker(0.03856129),
                StringChecker("[rgba, 114.0, 126.0, 141.0, 1.0]"),
            )
            val expectedAlternativeTrafficExpressionContents = listOf(
                StringChecker("step"),
                StringChecker("[line-progress]"),
                StringChecker("[rgba, 0.0, 0.0, 0.0, 0.0]"),
                DoubleChecker(0.0385612),
                StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
                DoubleChecker(0.6563122),
                StringChecker("[rgba, 190.0, 160.0, 135.0, 1.0]"),
                DoubleChecker(0.73853325),
                StringChecker("[rgba, 134.0, 148.0, 165.0, 1.0]"),
            )

            checkExpression(
                expectedPrimaryTrafficExpressionContents,
                result
                    .primaryRouteLineData
                    .dynamicData
                    .trafficExpressionProvider!!
                    .generateExpression()
            )
            checkExpression(
                expectedBaseExpressionContents,
                result
                    .alternativeRouteLinesData[0]
                    .dynamicData
                    .baseExpressionProvider
                    .generateExpression()
            )
            checkExpression(
                expectedCasingExpressionContents,
                result
                    .alternativeRouteLinesData[0]
                    .dynamicData
                    .casingExpressionProvider
                    .generateExpression()
            )
            checkExpression(
                expectedAlternativeTrafficExpressionContents,
                result
                    .alternativeRouteLinesData[0]
                    .dynamicData
                    .trafficExpressionProvider!!
                    .generateExpression()
            )
        }

    @Test
    fun getAlternativeRoutesDeviationOffsetsTest() {
        val routeData = FileUtils.loadJsonFixture("route_response_alternative_start.json")
        val response = DirectionsResponse.fromJson(routeData)
        val routeOptions = response.routes().first().routeOptions()!!
        val routes = NavigationRoute.create(
            directionsResponse = DirectionsResponse.fromJson(
                routeData
            ),
            routeOptions = routeOptions,
            routerOrigin = RouterOrigin.Offboard
        )
        val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
            every { navigationRoute } returns routes[1]
            every { forkIntersectionOfAlternativeRoute } returns mockk {
                every { location } returns mockk()
                every { geometryIndexInRoute } returns 2
                every { geometryIndexInLeg } returns 2
                every { legIndex } returns 0
            }
            every { forkIntersectionOfPrimaryRoute } returns mockk {
                every { location } returns mockk()
                every { geometryIndexInRoute } returns 2
                every { geometryIndexInLeg } returns 2
                every { legIndex } returns 0
            }
            every { infoFromFork } returns mockk {
                every { distance } returns 1588.7698034413877
                every { duration } returns 372.0307335579433
            }
            every { infoFromStartOfPrimary } returns mockk {
                every { distance } returns 1652.4918669972706
                every { duration } returns 400.6847335579434
            }
        }

        val result = MapboxRouteLineUtils.getAlternativeRouteDeviationOffsets(
            alternativeRouteMetadata
        )

        assertEquals(0.03856129838762756, result, 0.000000001)
    }

    @Test
    fun `getAlternativeRoutesDeviationOffsetsTest empty distances`() {
        val route = mockk<NavigationRoute> {
            every { id } returns "abc#0"
        }
        val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
            every { navigationRoute } returns route
        }

        val result = MapboxRouteLineUtils.getAlternativeRouteDeviationOffsets(
            alternativeRouteMetadata,
            distancesProvider = {
                RouteLineGranularDistances(
                    1.0,
                    emptyArray(),
                    emptyArray(),
                    emptyArray(),
                )
            }
        )

        assertEquals(0.0, result, 0.000000001)
        verify {
            logger.logW(
                "Remaining distances array size is 0 " +
                    "and the full distance is 1.0 - " +
                    "unable to calculate the deviation point of the alternative with ID " +
                    "'abc#0' to hide the portion that overlaps " +
                    "with the primary route.",
                "MapboxRouteLineUtils"
            )
        }
    }

    @Test
    fun `getAlternativeRoutesDeviationOffsetsTest full distance is zero`() {
        val route = mockk<NavigationRoute> {
            every { id } returns "abc#0"
        }
        val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
            every { navigationRoute } returns route
        }

        val result = MapboxRouteLineUtils.getAlternativeRouteDeviationOffsets(
            alternativeRouteMetadata,
            distancesProvider = {
                RouteLineGranularDistances(
                    0.0,
                    arrayOf(mockk(relaxed = true), mockk(relaxed = true)),
                    arrayOf(arrayOf(mockk(relaxed = true), mockk(relaxed = true))),
                    arrayOf(arrayOf(arrayOf(mockk(relaxed = true), mockk(relaxed = true)))),
                )
            }
        )

        assertEquals(0.0, result, 0.000000001)
        verify {
            logger.logW(
                "Remaining distances array size is 2 " +
                    "and the full distance is 0.0 - " +
                    "unable to calculate the deviation point of the alternative with ID " +
                    "'abc#0' to hide the portion that overlaps " +
                    "with the primary route.",
                "MapboxRouteLineUtils"
            )
        }
    }

    @Test
    fun `getAlternativeRoutesDeviationOffsetsTest remaining distance greater than full distance`() {
        val route = mockk<NavigationRoute> {
            every { id } returns "abc#0"
        }
        val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
            every { navigationRoute } returns route
            every { forkIntersectionOfAlternativeRoute } returns mockk {
                // the remaining distance at this index (30)
                // is greater than overall distance (15)
                every { geometryIndexInRoute } returns 1
            }
        }

        val result = MapboxRouteLineUtils.getAlternativeRouteDeviationOffsets(
            alternativeRouteMetadata,
            distancesProvider = {
                RouteLineGranularDistances(
                    completeDistance = 15.0,
                    routeDistances = arrayOf(
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 40.0
                        ),
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 30.0
                        ),
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 20.0
                        )
                    ),
                    legsDistances = emptyArray(),
                    stepsDistances = emptyArray(),
                )
            }
        )

        assertEquals(0.0, result, 0.000000001)
        verify {
            logger.logW(
                "distance remaining > full distance - " +
                    "unable to calculate the deviation point of the alternative with ID " +
                    "'abc#0' to hide the portion that overlaps " +
                    "with the primary route.",
                "MapboxRouteLineUtils"
            )
        }
    }

    @Test
    fun `getAlternativeRoutesDeviationOffsetsTest distance array out of bounds`() {
        val route = mockk<NavigationRoute> {
            every { id } returns "abc#0"
        }
        val alternativeRouteMetadata = mockk<AlternativeRouteMetadata> {
            every { navigationRoute } returns route
            every { forkIntersectionOfAlternativeRoute } returns mockk {
                every { geometryIndexInRoute } returns 3
            }
        }

        val result = MapboxRouteLineUtils.getAlternativeRouteDeviationOffsets(
            alternativeRouteMetadata,
            distancesProvider = {
                RouteLineGranularDistances(
                    completeDistance = 40.0,
                    routeDistances = arrayOf(
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 40.0
                        ),
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 30.0
                        ),
                        RouteLineDistancesIndex(
                            point = mockk(),
                            distanceRemaining = 20.0
                        )
                    ),
                    legsDistances = emptyArray(),
                    stepsDistances = emptyArray(),
                )
            }
        )

        assertEquals(0.0, result, 0.000000001)
        verify {
            logger.logW(
                "Remaining distance at index '3' requested but there are " +
                    "3 elements in the distances array - " +
                    "unable to calculate the deviation point of the alternative with ID " +
                    "'abc#0' to hide the portion that overlaps " +
                    "with the primary route.",
                "MapboxRouteLineUtils"
            )
        }
    }
}
