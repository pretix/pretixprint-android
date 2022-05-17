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
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.*
import eu.pretix.pretixprint.databinding.FragmentMaintenanceBinding
import java8.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.defaultSharedPreferences

private const val ARG_PRINTER_TYPE = "printer_type"

class MaintenanceFragment : DialogFragment(R.layout.fragment_maintenance) {
    enum class InputModes { HEX, ASCII }

    private var printerType: String? = null
    private var mode: InputModes = InputModes.HEX
    private lateinit var binding: FragmentMaintenanceBinding
    private lateinit var connection: ConnectionType
    private lateinit var streamHolder: CompletableFuture<StreamHolder>

    // FIXME: can the dialog close itself when app receives another intent?
    // FIXME: can the dialog close itself when app loses focus? (bad for copying)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            printerType = it.getString(ARG_PRINTER_TYPE)
        }
        val prefs = requireContext().defaultSharedPreferences
        val con = prefs.getString("hardware_${printerType}printer_connection", "network_printer")
        connection = getConnectionClass(con!!)!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // FIXME: make async, display some sort of connection indicator
        streamHolder = connection.connect(requireContext(), printerType!!)
        binding = FragmentMaintenanceBinding.inflate(layoutInflater)

        val modes = InputModes.values().map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modes)

        val validateTilInput = fun(text: Editable?) {
            if (mode == InputModes.HEX && text != null) {
                if (text.contains(Regex("[^a-fA-F0-9Xx ]"))) {
                    binding.tilInput.error = "Only hex characters allowed" // FIXME: extract string
                    return
                }
                if (text.trim().replace(Regex("\\s+"), "").replace(Regex("0[xX]"), "").length % 2 != 0) {
                    binding.tilInput.error = "Hex should appear in pairs of two" // FIXME: extract string
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
        (binding.tilType.editText as AutoCompleteTextView).apply {
            setAdapter(adapter)
            setText(mode.toString(), false)
            addTextChangedListener { text ->
                mode = InputModes.valueOf(text.toString())
                validateTilInput(binding.tilInput.editText!!.text)
            }
        }
        binding.btnSend.setOnClickListener {
            if (!binding.tilInput.error.isNullOrBlank()) {
                return@setOnClickListener
            }
            val data = binding.etInput.text.toString()
            send(mode, data)
            binding.etInput.text!!.clear()
        }


        if (streamHolder.isCompletedExceptionally) {
            var errorMsg = ""
            // extract exception message from future
            try {
                streamHolder.get()
            } catch (e: Exception) {
                errorMsg = e.message ?: ""
            }
            binding.tvError.text = errorMsg
            binding.tvError.visibility = VISIBLE
            binding.btnSend.isEnabled = false
        } else {
            streamHolder.thenAccept() { streamHolder ->
                //streamHolder.inputStream.bufferedReader()
                //binding.tvOutput
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
        if (streamHolder.isCompletedExceptionally) {
            return
        }
        val ba : ByteArray = if (mode == InputModes.HEX) {
            data.trim()
                .replace(Regex("\\s+"), "")
                .replace(Regex("0[xX]"), "")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } else {
            data.encodeToByteArray()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                streamHolder.get().outputStream.write(ba)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvError.text = e.message
                    binding.tvError.visibility = VISIBLE
                    binding.btnSend.isEnabled = false
                }
            }
        }
    }

    override fun onStop() {
        try {
            // FIXME: could crash, other scope?
            streamHolder.get().close()
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