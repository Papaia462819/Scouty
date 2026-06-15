package com.scouty.app.assistant.data

import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.AssistantRouteRecommendationRequest
import com.scouty.app.assistant.model.AssistantRouteRecommendationResult
import com.scouty.app.assistant.model.AssistantWeatherRequest
import com.scouty.app.assistant.model.AssistantWeatherResult
import com.scouty.app.assistant.model.GearItemDraft
import com.scouty.app.assistant.model.GearItemUpdate
import kotlinx.coroutines.flow.StateFlow

interface DeviceContextProvider {
    val deviceContext: StateFlow<DeviceContextSnapshot>
}

interface ChatActionHandler {
    fun toggleGearPacked(itemIds: List<String>, packed: Boolean)

    fun addGearItems(items: List<GearItemDraft>) = Unit

    fun removeGearItems(itemIds: List<String>) = Unit

    fun updateGearItems(updates: List<GearItemUpdate>) = Unit

    suspend fun queryWeather(request: AssistantWeatherRequest): AssistantWeatherResult =
        AssistantWeatherResult(
            available = false,
            isLive = false,
            locationLabel = request.locationLabel,
            summary = "Live weather is not available from this chat context.",
            errorMessage = "weather_handler_missing"
        )

    suspend fun queryRouteRecommendations(request: AssistantRouteRecommendationRequest): AssistantRouteRecommendationResult =
        AssistantRouteRecommendationResult(
            available = false,
            isLive = false,
            locationLabel = request.placeQuery,
            summary = "Route lookup by place is not available from this chat context.",
            errorMessage = "route_recommendation_handler_missing"
        )
}
