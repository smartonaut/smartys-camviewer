package de.smarty.camviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

class CameraAdapter(
    private val cameras: List<Camera>,
    private val onClick: (Camera) -> Unit,
    private val onLongClick: (Camera) -> Unit
) : RecyclerView.Adapter<CameraAdapter.Holder>() {

    /** Alle erstellten Player (fuer pauseAll/releaseAll ueber die Activity). */
    private val players = mutableSetOf<ExoPlayer>()

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
        holder.nameView.text = cam.name.ifBlank { holder.itemView.context.getString(R.string.label_camera_n, position + 1) }
        holder.cameraId = cam.id

        // Player pro Kachel (wiederverwenden)
        if (holder.player == null) {
            val p = ExoPlayer.Builder(holder.itemView.context).build()
            holder.player = p
            players.add(p)
            holder.playerView.player = p
            // WICHTIG: Controller aus, sonst schluckt PlayerView die Klicks
            // und die SingleView oeffnet sich nie.
            holder.playerView.useController = false
            holder.playerView.isClickable = true
        }
        val player = holder.player!!

        val url = cam.displayUrl()
        if (url.isNotBlank()) {
            val source = PlayerFactory.build(holder.itemView.context, url)
            player.setMediaSource(source)
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
        }

        holder.itemView.setOnClickListener { onClick(cam) }
        holder.itemView.setOnLongClickListener { onLongClick(cam); true }
        // Klick auch direkt aufs Video (sonst faengt die Surface ihn ab)
        holder.playerView.setOnClickListener { onClick(cam) }
        holder.playerView.setOnLongClickListener { onLongClick(cam); true }
    }

    override fun onViewRecycled(holder: Holder) {
        // stop() allein leakt die ExoPlayer-Instanz (Decoder-Threads,
        // Netwerk-Sockets); recycelte Kacheln muessen released werden.
        // onBindViewHolder baut den Player bei erneuter Bindung neu.
        releasePlayer(holder)
        super.onViewRecycled(holder)
    }

    private fun releasePlayer(holder: Holder) {
        holder.player?.let { players.remove(it); it.release() }
        holder.player = null
        holder.playerView.player = null
    }

    /** Alle Kachel-Streams pausieren (App/Activity in den Hintergrund). */
    fun pauseAll() {
        players.forEach { it.pause() }
    }

    /** Alle Player freigeben (Activity wird zerstoert). */
    fun releaseAll() {
        players.forEach { it.release() }
        players.clear()
    }

    override fun getItemCount(): Int = cameras.size
}
