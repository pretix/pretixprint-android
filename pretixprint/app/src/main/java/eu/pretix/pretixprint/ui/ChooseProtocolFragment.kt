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
import eu.pretix.pretixprint.byteprotocols.ByteProtocol
import eu.pretix.pretixprint.byteprotocols.protocols
import eu.pretix.pretixprint.databinding.ItemByteProtocolBinding
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.toast


class ByteProtocolDiffCallback : DiffUtil.ItemCallback<ByteProtocol<*>>() {
    override fun areItemsTheSame(oldItem: ByteProtocol<*>, newItem: ByteProtocol<*>): Boolean {
        return oldItem.identifier == newItem.identifier
    }

    override fun areContentsTheSame(oldItem: ByteProtocol<*>, newItem: ByteProtocol<*>): Boolean {
        return oldItem.identifier == newItem.identifier
    }
}

internal class ByteProtocolAdapter(var selectedValue: ByteProtocol<*>?) :
        ListAdapter<ByteProtocol<*>, BindingHolder<ItemByteProtocolBinding>>(ByteProtocolDiffCallback()),
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    var list: List<ByteProtocol<*>>? = null
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
        return BindingHolder(binding)
    }

    override fun onClick(v: View) {
        val binding = v.tag as ItemByteProtocolBinding
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

    override fun submitList(list: List<ByteProtocol<*>>?) {
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
        val view = inflater.inflate(R.layout.fragment_choose_byte_protocol, container, false)

        val current = (activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_mode"
        ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_mode", "")

        val adapter = ByteProtocolAdapter(protocols.firstOrNull {
            it.identifier == current
        })
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)

        adapter.submitList(protocols.filter {
            it.allowedForUsecase(useCase)
        })
        view.findViewById<RecyclerView>(R.id.list).adapter = adapter
        view.findViewById<RecyclerView>(R.id.list).layoutManager = layoutManager
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (adapter.selectedValue != null) {
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_mode", adapter.selectedValue!!.identifier)
                (activity as PrinterSetupActivity).startProtocolSettings()
            } else {
                toast(R.string.error_no_choice).show()
            }
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionSettings()
    }
}
