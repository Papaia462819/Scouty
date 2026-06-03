package com.scouty.app.assistant.model

enum class OfflineChatModelStatus {
    DISABLED,
    WAITING_METERED_CONFIRMATION,
    DOWNLOADING,
    INSTALLING,
    LOADING,
    READY,
    FAILED
}

data class OfflineChatModelState(
    val enabled: Boolean = false,
    val status: OfflineChatModelStatus = OfflineChatModelStatus.DISABLED,
    val progressPercent: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val modelSizeBytes: Long = 1_117_320_736L,
    val completedEventId: Long = 0L
) {
    val isBusy: Boolean
        get() = status == OfflineChatModelStatus.DOWNLOADING ||
            status == OfflineChatModelStatus.INSTALLING ||
            status == OfflineChatModelStatus.LOADING

    val canUseLocalModel: Boolean
        get() = enabled && status == OfflineChatModelStatus.READY
}

data class OfflineChatUiState(
    val enabled: Boolean = false,
    val status: OfflineChatModelStatus = OfflineChatModelStatus.DISABLED,
    val progressPercent: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val modelSizeBytes: Long = 1_117_320_736L,
    val completedEventId: Long = 0L,
    val showMeteredConfirmation: Boolean = false,
    val showLoadingDialog: Boolean = false,
    val showFinishedDialog: Boolean = false,
    val showDisableConfirmation: Boolean = false
) {
    val isBusy: Boolean
        get() = status == OfflineChatModelStatus.DOWNLOADING ||
            status == OfflineChatModelStatus.INSTALLING ||
            status == OfflineChatModelStatus.LOADING

    val canUseLocalModel: Boolean
        get() = enabled && status == OfflineChatModelStatus.READY
}
