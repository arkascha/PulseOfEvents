package org.rustygnome.pulse.ui.settings

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
import org.rustygnome.pulse.data.ResourceType

class ResourceAdapter(
    private val onEdit: (Resource) -> Unit,
    private val onDelete: (Resource) -> Unit,
    private val onPlay: (Resource) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : ListAdapter<Resource, ResourceAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtResourceName)
        val type: TextView = view.findViewById(R.id.txtResourceType)
        val playBtn: ImageButton = view.findViewById(R.id.btnPlayShortcut)
        val editBtn: ImageButton = view.findViewById(R.id.btnEdit)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        val dragHandle: ImageView = view.findViewById(R.id.imgDragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_resource, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resource = getItem(position)
        holder.name.text = resource.name
        holder.playBtn.setOnClickListener { onPlay(resource) }
        holder.editBtn.setOnClickListener { onEdit(resource) }
        holder.deleteBtn.setOnClickListener { onDelete(resource) }
        
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Resource>() {
        override fun areItemsTheSame(oldItem: Resource, newItem: Resource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Resource, newItem: Resource): Boolean {
            return oldItem == newItem
        }
    }
}
