package de.smarty.camviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

class CameraAdapter(
    private val cameras: List<Camera>,
    private val onClick: (Camera) -> Unit,
    private val onLongClick: (Camera) -> Unit
) : RecyclerView.Adapter<CameraAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val playerView: PlayerView = v.findViewById(R.id.player_view)
        val nameView: TextView = v.findViewById(R.id.cam_name)
        var player: ExoPlayer? = null
        var cameraId: String = ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_camera, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val cam = cameras[position]
        holder.nameView.text = cam.name.ifBlank { "Kamera ${position + 1}" }
        holder.cameraId = cam.id

        // Player pro Kachel (wiederverwenden)
        if (holder.player == null) {
            holder.player = ExoPlayer.Builder(holder.itemView.context).build()
            holder.playerView.player = holder.player
        }
        val player = holder.player!!

        val url = cam.displayUrl()
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }

        holder.itemView.setOnClickListener { onClick(cam) }
        holder.itemView.setOnLongClickListener { onLongClick(cam); true }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.player?.stop()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = cameras.size
}
