package com.mapbox.navigation.base.internal.utils

import com.google.gson.JsonSyntaxException
import com.mapbox.bindgen.DataRef
import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.ExpectedFactory
import com.mapbox.navigation.base.internal.route.toNavigationRoute
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigator.RouteAlternative
import com.mapbox.navigator.RouteInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException

suspend fun parseDirectionsResponse(
    dispatcher: CoroutineDispatcher,
    responseJson: DataRef,
    requestUrl: String,
    routerOrigin: RouterOrigin,
    responseTimeElapsedSeconds: Long
): Expected<Throwable, List<NavigationRoute>> =
    withContext(dispatcher) {
        return@withContext try {
            val routes = NavigationRoute.createAsync(
                directionsResponseJson = responseJson,
                routeRequestUrl = requestUrl,
                routerOrigin,
                responseTimeElapsedSeconds
            )
            if (routes.isEmpty()) {
                ExpectedFactory.createError(
                    IllegalStateException("no routes returned, collection is empty")
                )
            } else {
                ExpectedFactory.createValue(routes)
            }
        } catch (ex: Exception) {
            when (ex) {
                is JSONException,
                is JsonSyntaxException -> ExpectedFactory.createError(ex)
                else -> throw ex
            }
        }
    }

fun parseNativeDirectionsAlternative(
    routeAlternative: RouteAlternative,
    responseTimeElapsedSeconds: Long
): Expected<Throwable, NavigationRoute> {
    return parseRouteInterface(routeAlternative.route, responseTimeElapsedSeconds)
}

fun parseRouteInterface(
    route: RouteInterface,
    responseTimeElapsedSeconds: Long
): Expected<Throwable, NavigationRoute> {
    return try {
        val navigationRoute = route.toNavigationRoute(responseTimeElapsedSeconds)
        ExpectedFactory.createValue(navigationRoute)
    } catch (ex: Exception) {
        when (ex) {
            is JSONException,
            is IllegalStateException,
            is IllegalArgumentException -> ExpectedFactory.createError(ex)
            else -> throw ex
        }
    }
}
