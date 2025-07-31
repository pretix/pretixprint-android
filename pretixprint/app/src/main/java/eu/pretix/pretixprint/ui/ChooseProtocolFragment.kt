package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ByteProtocolInterface
import eu.pretix.pretixprint.byteprotocols.protocols
import eu.pretix.pretixprint.connections.connectionTypes
import eu.pretix.pretixprint.databinding.ItemByteProtocolBinding
import splitties.toast.toast


class ByteProtocolDiffCallback : DiffUtil.ItemCallback<ByteProtocolInterface<*>>() {
    override fun areItemsTheSame(oldItem: ByteProtocolInterface<*>, newItem: ByteProtocolInterface<*>): Boolean {
        return oldItem.identifier == newItem.identifier
    }

    override fun areContentsTheSame(oldItem: ByteProtocolInterface<*>, newItem: ByteProtocolInterface<*>): Boolean {
        return oldItem.identifier == newItem.identifier
    }
}

internal class ByteProtocolAdapter(var selectedValue: ByteProtocolInterface<*>?) :
        ListAdapter<ByteProtocolInterface<*>, BindingHolder<ItemByteProtocolBinding>>(ByteProtocolDiffCallback()),
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    var list: List<ByteProtocolInterface<*>>? = null
    private val CHECKED_CHANGE = 1

    override fun onBindViewHolder(holder: BindingHolder<ItemByteProtocolBinding>, position: Int) {
        val sp = getItem(position)
        holder.binding.ct = sp
        holder.binding.radioButton.isChecked = sp == selectedValue
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemByteProtocolBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.size > 0 && payloads.all { it == CHECKED_CHANGE }) {
            val value = getItem(position)
            holder.binding.radioButton.setOnCheckedChangeListener(null)
            holder.binding.radioButton.isChecked = value == selectedValue
            holder.binding.radioButton.setOnCheckedChangeListener(this)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemByteProtocolBinding> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemByteProtocolBinding.inflate(inflater, parent, false)
        binding.root.tag = binding
        binding.root.setOnClickListener(this)
        binding.radioButton.setOnCheckedChangeListener(this)
        return BindingHolder(binding)
    }

    override fun onClick(v: View) {
        val binding = v.tag as ItemByteProtocolBinding
        val previous = selectedValue
        selectedValue = binding.ct

        if (list != null) {
            try {
                if (previous != null) {
                    notifyItemChanged(list!!.indexOf(previous), CHECKED_CHANGE)
                }
                if (selectedValue != null) {
                    notifyItemChanged(list!!.indexOf(selectedValue!!), CHECKED_CHANGE)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCheckedChanged(v: CompoundButton, checked: Boolean) {
        onClick(v.parent as View)
    }

    override fun submitList(list: List<ByteProtocolInterface<*>>?) {
        this.list = list
        super.submitList(list)
    }
}

class ChooseByteProtocolFragment : SetupFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_choose_byte_protocol, container, false)

        val connectionIdentifier = (activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_connection"
        )
        val connection = connectionTypes.find { it.identifier == connectionIdentifier }!!

        val current = (activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_mode"
        ) ?: prefs.getString("hardware_${useCase}printer_mode", "")

        val adapter = ByteProtocolAdapter(protocols.firstOrNull {
            it.identifier == current
        })
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)

        adapter.submitList(protocols.filter {
            it.allowedForUsecase(useCase) && it.allowedForConnection(connection)
        })
        view.findViewById<RecyclerView>(R.id.list).adapter = adapter
        view.findViewById<RecyclerView>(R.id.list).layoutManager = layoutManager
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (adapter.selectedValue != null) {
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_mode", adapter.selectedValue!!.identifier)
                (activity as PrinterSetupActivity).startProtocolSettings()
            } else {
                toast(R.string.error_no_choice)
            }
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionSettings(true)
    }
}
