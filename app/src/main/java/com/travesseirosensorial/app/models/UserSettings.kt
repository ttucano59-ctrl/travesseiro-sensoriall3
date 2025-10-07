package com.travesseirosensorial.app.models

import java.io.Serializable

data class UserSettings(
    var touchSensitivity: Int = 50,
    var massageType: MassageType = MassageType.VIBRATION,
    var autoActivateBpmLimit: Int = 0
) : Serializable {
    enum class MassageType(val displayName: String) : Serializable {
        VIBRATION("Vibração"),
        PRESSURE("Pressão"),
        CIRCULAR("Circular"),
        COMBINED("Combinada")
    }
}
