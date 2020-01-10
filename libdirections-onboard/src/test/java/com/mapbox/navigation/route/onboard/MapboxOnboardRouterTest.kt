package com.mapbox.navigation.route.onboard

import com.mapbox.navigation.navigator.MapboxNativeNavigator
import com.mapbox.navigation.navigator.MapboxNativeNavigatorImpl
import com.mapbox.navigation.route.onboard.model.Config
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MapboxOnboardRouterTest {
    private lateinit var onboardRouter: MapboxOnboardRouter
    private val navigator: MapboxNativeNavigator = mockk()
    private val tilePath = "tiles"

    @Before
    fun setUp() {
        onboardRouter = MapboxOnboardRouter(navigator, Config(tilePath))
    }

    @Test
    fun generationSanityTest() {
        Assert.assertNotNull(onboardRouter)
    }
}