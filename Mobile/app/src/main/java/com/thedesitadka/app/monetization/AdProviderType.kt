package com.thedesitadka.app.monetization

enum class AdProviderType(val displayName: String) {
    EXOCLICK("ExoClick"),
    JUICYADS("JuicyAds")
}

enum class AdProviderState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    LOADING,
    LOADED,
    DISPLAYING,
    FAILED,
    UNAVAILABLE,
    DESTROYED
}

enum class AdPlacementType(val trackingKey: String) {
    HOME_FEED("home_feed"),
    CONTENT_DETAIL("content_detail"),
    PLAYER_COMPANION("player_companion"),
    PLAYER_PREROLL("player_preroll"),
    DOWNLOAD_SCREEN("download_screen")
}

enum class AdFormat(val widthDp: Int, val heightDp: Int) {
    BANNER_300x250(300, 250),
    BANNER_728x90(728, 90),
    BANNER_300x100(300, 100),
    VAST_VIDEO(0, 0),
    NATIVE_CARD(0, 0)
}

enum class AdProviderMode {
    AUTO_FALLBACK,
    EXOCLICK_ONLY,
    JUICYADS_ONLY,
    DISABLED
}
