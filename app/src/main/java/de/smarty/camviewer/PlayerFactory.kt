package de.smarty.camviewer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

/**
 * Baut fuer eine Kamera-URL das passende MediaSource.
 * WICHTIG: go2rtc (und viele Billigkameras) sprechen RTSP NUR ueber TCP.
 * ExoPlayer versucht standardmaessig UDP -> Stream bleibt schwarz.
 * Daher hier setForceUseRtpTcp(true).
 */
object PlayerFactory {

    fun build(context: Context, url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)

        if (url.startsWith("rtsp://") || url.startsWith("rtsps://")) {
            val rtspFactory = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(15000L)
            return rtspFactory.createMediaSource(mediaItem)
        }

        // HLS/HTTP/etc. -> Default-Weg
        return DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
    }
}
