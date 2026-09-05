package com.anantmittal.cartbridge.app

import kotlinx.serialization.Serializable

sealed interface Route {
    val path: String
        get() = this::class.qualifiedName ?: ""

    @Serializable
    data object DefaultNavGraph : Route

    @Serializable
    data object Splash : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object Preview : Route

    @Serializable
    data object Settings : Route
}
