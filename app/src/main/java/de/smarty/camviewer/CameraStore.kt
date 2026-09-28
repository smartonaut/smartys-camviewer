package de.smarty.camviewer

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object CameraStore {
    private const val PREFS = "camviewer_prefs"
    private const val KEY = "cameras"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(ctx: Context): List<Camera> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString<CameraList>(raw).cameras
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, cameras: List<Camera>) {
        val data = json.encodeToString(CameraList(cameras))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, data).apply()
    }

    fun encodeCamera(cam: Camera): String = json.encodeToString(cam)

    fun decodeCamera(raw: String): Camera? = try {
        json.decodeFromString<Camera>(raw)
    } catch (e: Exception) {
        null
    }
}
