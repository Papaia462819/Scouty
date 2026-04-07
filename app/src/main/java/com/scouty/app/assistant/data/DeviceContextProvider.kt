package com.scouty.app.assistant.data

import com.scouty.app.assistant.model.DeviceContextSnapshot
import kotlinx.coroutines.flow.StateFlow

interface DeviceContextProvider {
    val deviceContext: StateFlow<DeviceContextSnapshot>
}

interface ChatActionHandler {
    fun toggleGearPacked(itemIds: List<String>, packed: Boolean)
}
