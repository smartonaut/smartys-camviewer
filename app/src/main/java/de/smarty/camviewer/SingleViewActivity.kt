package de.smarty.camviewer

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SingleViewActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var cam: Camera? = null
    private var ptzPanelVisible = false
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single)

        val camJson = intent.getStringExtra("camera")
        // Ungueltige/fehlende Kamera-Daten -> Activity direkt beenden,
        // sonst haengt ein schwarzer Screen ohne Funktion.
        cam = camJson?.let { CameraStore.decodeCamera(it) } ?: run { finish(); return }

        val pv = findViewById<PlayerView>(R.id.player_view)
        findViewById<TextView>(R.id.tv_title).text = cam?.name ?: getString(R.string.label_camera)
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        // Player
        val url = cam?.displayUrl()
        if (!url.isNullOrBlank()) {
            val p = ExoPlayer.Builder(this).build()
            player = p
            pv.player = p
            val source = PlayerFactory.build(this, url)
            p.setMediaSource(source)
            p.prepare()
            p.playWhenReady = true
        }

        setupPtz()
    }

    private fun setupPtz() {
        val c = cam ?: return
        val panel = findViewById<LinearLayout>(R.id.ptz_panel)
        val toggle = findViewById<Button>(R.id.btn_ptz_toggle)

        if (!c.ptzEnabled || c.ptzHost.isBlank()) {
            panel.visibility = android.view.View.GONE
            toggle.visibility = android.view.View.GONE
            return
        }

        // Toggle-Button zeigen, Panel startet ausgeblendet
        toggle.visibility = android.view.View.VISIBLE
        panel.visibility = android.view.View.GONE
        ptzPanelVisible = false

        toggle.setOnClickListener {
            ptzPanelVisible = !ptzPanelVisible
            panel.visibility = if (ptzPanelVisible) android.view.View.VISIBLE
                               else android.view.View.GONE
            toggle.isSelected = ptzPanelVisible
        }

        // Festpositionen laden
        val presetRow = findViewById<LinearLayout>(R.id.preset_row)
        scope.launch {
            val presets = OnvifPtz.getPresets(c)
            // Activity kann waehrend des Netzwerk-Calls beendet worden sein
            // -> Views nicht mehr anfassen.
            if (isFinishing || isDestroyed) return@launch
            val shown = presets.take(8)
            for (p in shown) {
                val b = Button(this@SingleViewActivity)
                b.text = p.name
                b.setOnClickListener {
                    scope.launch { OnvifPtz.gotoPreset(c, p.token) }
                }
                presetRow.addView(b)
            }
        }

        // Steuerkreuz: halten = bewegen, loslassen = stop
        val moves = mapOf(
            R.id.ptz_up to Triple(0f, 1f, 0f),
            R.id.ptz_down to Triple(0f, -1f, 0f),
            R.id.ptz_left to Triple(-1f, 0f, 0f),
            R.id.ptz_right to Triple(1f, 0f, 0f),
            R.id.ptz_zoom_in to Triple(0f, 0f, 1f),
            R.id.ptz_zoom_out to Triple(0f, 0f, -1f)
        )

        for ((id, vec) in moves) {
            val btn = findViewById<Button>(id)
            var moveJob: Job? = null
            btn.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        moveJob = scope.launch {
                            while (true) {
                                OnvifPtz.continuousMove(c, vec.first, vec.second, vec.third)
                                delay(800)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        moveJob?.cancel()
                        scope.launch { OnvifPtz.stop(c) }
                        true
                    }
                    else -> false
                }
            }
        }

        // Home -> Preset 1
        findViewById<Button>(R.id.ptz_home).setOnClickListener {
            scope.launch { OnvifPtz.gotoPreset(c, "1") }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stream läuft weiter, wenn die Activity wieder im Vordergrund ist.
        player?.play()
    }

    override fun onStop() {
        super.onStop()
        // Stream pausieren: sonst läuft RTSP im Hintergrund weiter
        // (Akku, Bandbreite, Netzwerklast im MultiView-Betrieb).
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Alle laufenden PTZ-Jobs abbrechen (ContinuousMove-Schleife,
        // Preset-Loads) — sonst leben die Coroutines ueber die Activity hinaus.
        scope.cancel()
        player?.release()
        player = null
    }
}
