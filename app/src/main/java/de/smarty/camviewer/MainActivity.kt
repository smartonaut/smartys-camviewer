package de.smarty.camviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var adapter: CameraAdapter
    private var cameras: MutableList<Camera> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        val cols = if (resources.configuration.smallestScreenWidthDp >= 600) 2 else 1
        grid.layoutManager = GridLayoutManager(this, cols)

        cameras = CameraStore.load(this).toMutableList()
        adapter = CameraAdapter(cameras,
            onClick = { openSingleView(it) },
            onLongClick = { showCameraMenu(it) })
        grid.adapter = adapter

        findViewById<Button>(R.id.btn_add).setOnClickListener { showEditDialog(null) }
    }

    private fun openSingleView(cam: Camera) {
        val intent = Intent(this, SingleViewActivity::class.java)
        intent.putExtra("camera", CameraStore.encodeCamera(cam))
        startActivity(intent)
    }

    private fun showCameraMenu(cam: Camera) {
        AlertDialog.Builder(this)
            .setTitle(cam.name.ifBlank { "Kamera" })
            .setItems(arrayOf("Bearbeiten", "Löschen")) { _, which ->
                when (which) {
                    0 -> showEditDialog(cam)
                    1 -> confirmDelete(cam)
                }
            }
            .show()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        CameraStore.save(this, cameras)
    }

    private fun showEditDialog(existing: Camera?) {
        val view = layoutInflater.inflate(R.layout.dialog_camera, null)
        val inName = view.findViewById<EditText>(R.id.in_name)
        val inRtsp = view.findViewById<EditText>(R.id.in_rtsp)
        val inBackend = view.findViewById<EditText>(R.id.in_backend)
        val inCamId = view.findViewById<EditText>(R.id.in_camid)
        val rbRtsp = view.findViewById<RadioButton>(R.id.rb_rtsp)
        val rbBackend = view.findViewById<RadioButton>(R.id.rb_backend)

        // ONVIF/PTZ-Felder
        val cbPtz = view.findViewById<CheckBox>(R.id.cb_ptz)
        val inPtzHost = view.findViewById<EditText>(R.id.in_ptz_host)
        val inPtzPort = view.findViewById<EditText>(R.id.in_ptz_port)
        val inPtzUser = view.findViewById<EditText>(R.id.in_ptz_user)
        val inPtzPass = view.findViewById<EditText>(R.id.in_ptz_pass)

        if (existing != null) {
            inName.setText(existing.name)
            inRtsp.setText(existing.rtspUrl)
            inBackend.setText(existing.backendBase)
            inCamId.setText(existing.backendCamId)
            if (existing.sourceType == "backend") rbBackend.isChecked = true
            cbPtz.isChecked = existing.ptzEnabled
            inPtzHost.setText(existing.ptzHost)
            inPtzPort.setText(existing.ptzPort.toString())
            inPtzUser.setText(existing.ptzUser)
            inPtzPass.setText(existing.ptzPass)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Kamera hinzufügen" else "Kamera bearbeiten")
            .setView(view)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = inName.text.toString().trim()
                val sourceType = if (rbBackend.isChecked) "backend" else "rtsp"

                if (name.isBlank()) {
                    inName.error = "Name fehlt"
                    return@setOnClickListener
                }
                if (sourceType == "rtsp" && inRtsp.text.isNullOrBlank()) {
                    inRtsp.error = "RTSP-URL fehlt"
                    return@setOnClickListener
                }
                if (cbPtz.isChecked && inPtzHost.text.isNullOrBlank()) {
                    inPtzHost.error = "PTZ-Host fehlt"
                    return@setOnClickListener
                }

                val cam = (existing ?: Camera()).copy(
                    name = name,
                    sourceType = sourceType,
                    rtspUrl = inRtsp.text.toString().trim(),
                    backendBase = inBackend.text.toString().trim().ifBlank { "http://10.10.23.34:8090" },
                    backendCamId = inCamId.text.toString().trim(),
                    ptzEnabled = cbPtz.isChecked,
                    ptzHost = inPtzHost.text.toString().trim(),
                    ptzPort = inPtzPort.text.toString().trim().toIntOrNull() ?: 80,
                    ptzUser = inPtzUser.text.toString().trim().ifBlank { "admin" },
                    ptzPass = inPtzPass.text.toString()
                )

                if (existing == null) cameras.add(cam)
                else {
                    val i = cameras.indexOfFirst { it.id == existing.id }
                    if (i >= 0) cameras[i] = cam
                }
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(cam: Camera) {
        AlertDialog.Builder(this)
            .setTitle("Kamera löschen")
            .setMessage("${cam.name.ifBlank { "Kamera" }} entfernen?")
            .setPositiveButton("Löschen") { _, _ ->
                cameras.removeAll { it.id == cam.id }
                refresh()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Player werden vom Adapter via onViewRecycled gestoppt
    }
}
