package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.connectionTypes
import eu.pretix.pretixprint.databinding.ItemConnectionTypeBinding
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.toast


class ConnectionTypeDiffCallback : DiffUtil.ItemCallback<ConnectionType>() {
    override fun areItemsTheSame(oldItem: ConnectionType, newItem: ConnectionType): Boolean {
        return oldItem.identifier == newItem.identifier
    }

    override fun areContentsTheSame(oldItem: ConnectionType, newItem: ConnectionType): Boolean {
        return oldItem.identifier == newItem.identifier
    }
}

internal class ConnectionTypeAdapter(var selectedValue: ConnectionType?) :
        ListAdapter<ConnectionType, BindingHolder<ItemConnectionTypeBinding>>(ConnectionTypeDiffCallback()),
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    var list: List<ConnectionType>? = null
    private val CHECKED_CHANGE = 1

    override fun onBindViewHolder(holder: BindingHolder<ItemConnectionTypeBinding>, position: Int) {
        val sp = getItem(position)
        holder.binding.ct = sp
        holder.binding.radioButton.isChecked = sp == selectedValue
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemConnectionTypeBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.size > 0 && payloads.all { it == CHECKED_CHANGE }) {
            val value = getItem(position)
            holder.binding.radioButton.setOnCheckedChangeListener(null)
            holder.binding.radioButton.isChecked = value == selectedValue
            holder.binding.radioButton.setOnCheckedChangeListener(this)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemConnectionTypeBinding> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemConnectionTypeBinding.inflate(inflater, parent, false)
        binding.root.tag = binding
        binding.root.setOnClickListener(this)
        return BindingHolder(binding)
    }

    override fun onClick(v: View) {
        val binding = v.tag as ItemConnectionTypeBinding
        val previous = selectedValue
        selectedValue = binding.ct

        if (list != null) {
            if (previous != null) {
                notifyItemChanged(list!!.indexOf(previous), CHECKED_CHANGE)
            }
            if (selectedValue != null) {
                notifyItemChanged(list!!.indexOf(selectedValue!!), CHECKED_CHANGE)
            }
        }
    }

    override fun onCheckedChanged(v: CompoundButton?, checked: Boolean) {
        onClick(v?.parent as View)
    }

    override fun submitList(list: List<ConnectionType>?) {
        this.list = list
        super.submitList(list)
    }
}

class ChooseConnectionTypeFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_choose_connection_type, container, false)

        val adapter = ConnectionTypeAdapter(connectionTypes.firstOrNull {
            it.identifier == defaultSharedPreferences.getString("hardware_${useCase}printer_connection", "")
        })
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)

        adapter.submitList(connectionTypes.filter {
            it.allowedForUsecase(useCase)
        })
        view.findViewById<RecyclerView>(R.id.list).adapter = adapter
        view.findViewById<RecyclerView>(R.id.list).layoutManager = layoutManager
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (adapter.selectedValue != null) {
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_connection", adapter.selectedValue!!.identifier)
            } else {
                toast(R.string.error_no_choice).show()
            }
        }

        return view
    }
}
