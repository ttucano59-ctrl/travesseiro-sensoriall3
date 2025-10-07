package com.travesseirosensorial.app.models

data class PillowStatus(
    var isConnected: Boolean = false,
    var isActive: Boolean = false,
    var batteryLevel: Int = 100,
    var intensity: Int = 50,
    var connectedDeviceName: String? = null
)
