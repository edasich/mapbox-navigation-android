package com.mapbox.navigation.dropin.infopanel

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.dropin.R
import com.mapbox.navigation.dropin.extendablebutton.StartNavigationButtonComponent
import com.mapbox.navigation.dropin.navigationview.NavigationViewContext
import com.mapbox.navigation.dropin.navigationview.NavigationViewModel
import com.mapbox.navigation.dropin.testutil.TestLifecycleOwner
import com.mapbox.navigation.dropin.util.TestStore
import com.mapbox.navigation.dropin.util.TestingUtil.findComponent
import com.mapbox.navigation.testing.LoggingFrontendTestRule
import com.mapbox.navigation.testing.MainCoroutineRule
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.base.lifecycle.UIComponent
import com.mapbox.navigation.ui.tripprogress.internal.ui.TripProgressComponent
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InfoPanelHeaderRoutesPreviewBinderTest {

    @get:Rule
    val loggerRule = LoggingFrontendTestRule()

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var ctx: Context
    private lateinit var navContext: NavigationViewContext
    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var sut: InfoPanelHeaderRoutesPreviewBinder

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        navContext = NavigationViewContext(
            context = ctx,
            lifecycleOwner = TestLifecycleOwner(),
            viewModel = NavigationViewModel(),
            storeProvider = { TestStore() }
        )
        mapboxNavigation = mockk(relaxed = true)

        sut = InfoPanelHeaderRoutesPreviewBinder(navContext)
    }

    @Test
    fun `bind should inflate route preview header layout`() {
        val rootLayout = FrameLayout(ctx)

        sut.bind(rootLayout)

        assertNotNull(rootLayout.findViewById(R.id.tripProgressLayout))
        assertNotNull(rootLayout.findViewById(R.id.startNavigationContainer))
    }

    @Test
    fun `bind should return MapboxNavigationObserverChain with TripProgressComponent`() {
        val components = sut.bind(FrameLayout(ctx))
        components.onAttached(mapboxNavigation)

        assertNotNull(components.findComponent { it is TripProgressComponent })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `bind should NOT bind TripProgressComponent when ViewOptionsCustomization showTripProgress is FALSE`() {
        navContext.applyOptionsCustomization {
            showTripProgress = false
        }
        val components = sut.bind(FrameLayout(ctx))
        components.onAttached(mapboxNavigation)

        assertNull(components.findComponent { it is TripProgressComponent })
    }

    @Test
    fun `bind should return MapboxNavigationObserverChain with StartNavigationButtonComponent`() {
        val components = sut.bind(FrameLayout(ctx))
        components.onAttached(mapboxNavigation)

        assertNotNull(components.findComponent { it is StartNavigationButtonComponent })
    }

    @Test
    @Suppress("MaxLineLength")
    fun `bind should NOT bind StartNavigationButtonComponent when ViewOptionsCustomization showStartNavigationButton is FALSE`() {
        navContext.applyOptionsCustomization {
            showStartNavigationButton = false
        }
        val components = sut.bind(FrameLayout(ctx))
        components.onAttached(mapboxNavigation)

        assertNull(components.findComponent { it is StartNavigationButtonComponent })
    }

    @Test
    fun `bind should use custom binder for Start Navigation Button`() {
        class MyStartButtonComponent : UIComponent()
        navContext.applyBinderCustomization {
            infoPanelStartNavigationButtonBinder = UIBinder {
                MyStartButtonComponent()
            }
        }

        val components = sut.bind(FrameLayout(ctx))
        components.onAttached(mapboxNavigation)

        assertNotNull(components.findComponent { it is MyStartButtonComponent })
        assertNull(
            "should NOT bind default StartNavigationButtonComponent",
            components.findComponent { it is StartNavigationButtonComponent }
        )
    }
}
