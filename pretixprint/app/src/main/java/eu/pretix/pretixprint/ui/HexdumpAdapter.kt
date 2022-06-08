package eu.pretix.pretixprint.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.util.DirectedHexdumpByteArrayHolder
import eu.pretix.pretixprint.util.DirectedHexdumpCollection

class HexdumpAdapter:
    ListAdapter<DirectedHexdumpByteArrayHolder, HexdumpAdapter.HexdumpViewHolder>(HexdumpDiffCallback) {

    class HexdumpViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDirection: TextView = itemView.findViewById(R.id.tvDirection)
        private val tvHex: TextView = itemView.findViewById(R.id.tvHex)
        private val tvAscii: TextView = itemView.findViewById(R.id.tvAscii)
        private var currentHexdump: DirectedHexdumpByteArrayHolder? = null


        fun bind(item: DirectedHexdumpByteArrayHolder) {
            currentHexdump = item

            tvDirection.text = item.direction.toString()
            tvHex.text = item.toHex()
            tvAscii.text = item.toAscii()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HexdumpViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_maintenance_row, parent, false)
        return HexdumpViewHolder(view)
    }

    override fun onBindViewHolder(holder: HexdumpViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

object HexdumpDiffCallback : DiffUtil.ItemCallback<DirectedHexdumpByteArrayHolder>() {
    override fun areItemsTheSame(oldItem: DirectedHexdumpByteArrayHolder, newItem: DirectedHexdumpByteArrayHolder): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: DirectedHexdumpByteArrayHolder, newItem: DirectedHexdumpByteArrayHolder): Boolean {
        return oldItem.same(newItem)
    }
}

class AdapterBoundDirectedHexdumpCollection(maxSize: Int = 16, val listAdapter: HexdumpAdapter): DirectedHexdumpCollection(maxSize) {
    init {
        listAdapter.submitList(collection)
    }

    fun pushBytesAndNotify(dir: DirectedHexdumpByteArrayHolder.Direction, ba: ByteArray, forceNew: Boolean = false) {
        var fN = forceNew
        ba.forEach {
            pushByteAndNotify(dir, it, fN)
            fN = false
        }
    }

    fun pushByteAndNotify(dir: DirectedHexdumpByteArrayHolder.Direction, b: Byte, forceNew: Boolean = false) {
        var new = false
        if (collection.isEmpty() || forceNew) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
            new = true
        }
        if (collection.last().direction != dir) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
            new = true
        }
        if (collection.last().isFull()) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
            new = true
        }
        collection.last().push(b)
        if (new) {
            listAdapter.notifyItemInserted(collection.size - 1)
        } else {
            listAdapter.notifyItemChanged(collection.size - 1)
        }
    }
}