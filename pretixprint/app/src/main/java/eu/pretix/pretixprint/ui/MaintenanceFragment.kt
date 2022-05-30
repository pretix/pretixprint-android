package eu.pretix.pretixprint.ui

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.*
import eu.pretix.pretixprint.databinding.FragmentMaintenanceBinding
import eu.pretix.pretixprint.util.DirectedHexdumpCollection
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.IOException

private const val ARG_PRINTER_TYPE = "printer_type"

class MaintenanceFragment : DialogFragment(R.layout.fragment_maintenance) {
    enum class InputModes { HEX, ASCII }

    private var printerType: String? = null
    private var mode: InputModes = InputModes.HEX
    private lateinit var binding: FragmentMaintenanceBinding
    private lateinit var connection: ConnectionType
    private var streamHolder: StreamHolder? = null
    private var responseListener: Job? = null
    private var hexdump = DirectedHexdumpCollection(8)
    private var sendNewline = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            printerType = it.getString(ARG_PRINTER_TYPE)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val con = prefs.getString("hardware_${printerType}printer_connection", "network_printer")
        connection = getConnectionClass(con!!)!!
    }

    fun fail(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = VISIBLE
        binding.btnSend.isEnabled = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentMaintenanceBinding.inflate(layoutInflater)

        val modes = InputModes.values().map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modes)

        val validateTilInput = fun(text: Editable?) {
            if (mode == InputModes.HEX && text != null) {
                if (text.replace(Regex("0[xX]([a-fA-F0-9]{2})"), "$1").contains(Regex("[^a-fA-F0-9 ]"))) {
                    binding.tilInput.error = getString(R.string.maintain_error_hex_only)
                    return
                }
                if (text.trim().replace(Regex("\\s+"), "").replace(Regex("0[xX]([a-fA-F0-9]{2})"), "$1").length % 2 != 0) {
                    binding.tilInput.error = getString(R.string.maintain_error_hex_pairs)
                    return
                }
            }
            binding.tilInput.error = ""
        }
        binding.tilInput.editText!!.apply {
            addTextChangedListener(afterTextChanged = validateTilInput)
            setOnEditorActionListener { _, actionId, _ ->
                return@setOnEditorActionListener when (actionId) {
                    EditorInfo.IME_ACTION_SEND -> {
                        binding.btnSend.performClick()
                        true
                    }
                    else -> false
                }
            }
        }
        binding.tilInput.setEndIconOnClickListener {
            sendNewline = !sendNewline
            with(binding.tilInput) {
                setEndIconDrawable(if (sendNewline) R.drawable.ic_keyboard_return_24dp else R.drawable.ic_keyboard_no_return_24dp)
                endIconContentDescription = getString(if (sendNewline) R.string.maintain_newline else R.string.maintain_newline_disabled)
            }
        }
        (binding.tilType.editText as AutoCompleteTextView).apply {
            setAdapter(adapter)
            setText(mode.toString(), false)
            addTextChangedListener { text ->
                mode = InputModes.valueOf(text.toString())
                validateTilInput(binding.tilInput.editText!!.text)
            }
        }
        binding.btnSend.apply {
            setOnClickListener {
                if (!binding.tilInput.error.isNullOrBlank()) {
                    return@setOnClickListener
                }
                val data = binding.etInput.text.toString()
                send(mode, data)
                binding.etInput.text!!.clear()
            }
            isEnabled = false
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    streamHolder = connection.connectAsync(requireContext(), printerType!!)
                }
            } catch (e: Exception) {
                fail(e.message ?: getString(R.string.maintain_error_connection_fail))
                return@launch
            } catch (e: NotImplementedError) {
                fail(e.message!!)
                return@launch
            }

            binding.btnSend.isEnabled = true

            responseListener = lifecycleScope.launch(Dispatchers.IO) {
                val dis = DataInputStream(streamHolder!!.inputStream)
                while (isActive) {
                    try {
                        val byte = dis.readByte()
                        withContext(Dispatchers.Main) {
                            hexdump.pushByte(DirectedHexdumpCollection.Direction.IN, byte)
                            binding.tvOutput.text = hexdump.toString()
                        }
                    } catch (e: IOException) {
                        if (!isActive) break // got canceled
                        withContext(Dispatchers.Main) {
                            fail(e.message ?: getString(R.string.maintain_error_connection_lost))
                        }
                        break
                    }
                }
            }
        }

        val typeRef = resources.getIdentifier("settings_label_${printerType}printer", "string", requireContext().packageName)
        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_dialog_maintenance, getString(typeRef)))
            .setPositiveButton(getString(R.string.dismiss)) { _, _ -> }
            .setView(binding.root)
            .create()
    }

    fun send(mode: InputModes, data: String) {
        if (streamHolder == null) {
            return
        }
        var ba : ByteArray = if (mode == InputModes.HEX) {
            data.trim()
                .replace(Regex("\\s+"), "")
                .replace(Regex("0[xX]([a-fA-F0-9 ]{2})"), "$1")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } else {
            data.encodeToByteArray()
        }
        if (sendNewline) {
            ba += byteArrayOf('\n'.code.toByte())
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                streamHolder!!.outputStream.write(ba)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    fail(e.message ?: getString(R.string.maintain_error_send_fail))
                }
            }
        }
        hexdump.pushBytes(DirectedHexdumpCollection.Direction.OUT, ba, true)
    }

    override fun onStop() {
        try {
            responseListener?.cancel()
            lifecycleScope.launch(Dispatchers.IO) {
                streamHolder?.close()
            }
        } catch(e: Exception) { } // ignore
        super.onStop()
    }

    companion object {
        @JvmStatic
        fun newInstance(printerType: String) =
            MaintenanceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRINTER_TYPE, printerType)
                }
            }
    }
}
