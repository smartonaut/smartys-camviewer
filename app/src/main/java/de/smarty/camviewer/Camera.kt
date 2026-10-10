package de.smarty.camviewer

import kotlinx.serialization.Serializable

@Serializable
data class Camera(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val sourceType: String = "rtsp",       // "rtsp" oder "backend"
    val rtspUrl: String = "",               // fuer sourceType=rtsp
    val backendCamId: String = "",          // fuer sourceType=backend (go2rtc src)
    val backendBase: String = "",
    val username: String = "",
    val password: String = "",
    // ONVIF-PTZ (optional, fuer Kameras mit Schwenk/Neigen/Zoom)
    val ptzEnabled: Boolean = false,
    val ptzHost: String = "",
    val ptzPort: Int = 80,
    val ptzPath: String = "/onvif/ptz",
    val ptzUser: String = "admin",
    val ptzPass: String = "admin",
    val ptzProfile: String = "protoken_1"
) {
    fun displayUrl(): String {
        return when (sourceType) {
            "backend" -> "$backendBase/api/stream/$backendCamId"
            else -> rtspUrl
        }
    }
}

@Serializable
data class CameraList(
    val cameras: List<Camera> = emptyList()
)
