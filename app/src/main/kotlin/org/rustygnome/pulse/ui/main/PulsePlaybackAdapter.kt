package org.rustygnome.pulse.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.rustygnome.pulse.R
import org.rustygnome.pulse.data.Resource

class PulsePlaybackAdapter(
    private val onPlayPauseClick: (Resource, Boolean) -> Unit,
    private val onInfoClick: (Resource) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : ListAdapter<Resource, PulsePlaybackAdapter.ViewHolder>(DiffCallback) {

    private var playingResourceId: Long = -1L

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtPulseName)
        val infoBtn: ImageButton = view.findViewById(R.id.btnInfo)
        val playPauseBtn: ImageButton = view.findViewById(R.id.btnPlayPause)
        val dragHandle: ImageView = view.findViewById(R.id.imgDragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pulse_playback, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resource = getItem(position)
        holder.name.text = resource.name

        // Show pause icon only if this specific resource is active
        if (resource.id == playingResourceId) {
            holder.playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            holder.playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        }

        holder.infoBtn.setOnClickListener { onInfoClick(resource) }

        holder.playPauseBtn.setOnClickListener {
            // If it's currently playing, we want to stop it. Otherwise, we want to play it.
            val shouldPlay = resource.id != playingResourceId
            onPlayPauseClick(resource, shouldPlay)
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    fun setPlayingResource(resourceId: Long) {
        val previousId = playingResourceId
        playingResourceId = resourceId
        
        // Refresh the affected items to update icons
        val prevIndex = currentList.indexOfFirst { it.id == previousId }
        val newIndex = currentList.indexOfFirst { it.id == resourceId }
        
        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    fun clearPlayingResource() {
        val previousId = playingResourceId
        playingResourceId = -1L
        val prevIndex = currentList.indexOfFirst { it.id == previousId }
        if (prevIndex != -1) notifyItemChanged(prevIndex)
    }

    object DiffCallback : DiffUtil.ItemCallback<Resource>() {
        override fun areItemsTheSame(oldItem: Resource, newItem: Resource): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Resource, newItem: Resource): Boolean = oldItem == newItem
    }
}
