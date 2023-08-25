package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.github.razir.progressbutton.attachTextChangeAnimator
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.pretix.pretixprint.print.testPrint
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinishSettingsFragment : SetupFragment() {
    val bgScope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val activity = activity as PrinterSetupActivity
        val view = inflater.inflate(R.layout.fragment_finish_settings, container, false)

        val testPageButton = view.findViewById<Button>(R.id.btnTestPage)
        activity.bindProgressButton(testPageButton)
        testPageButton.attachTextChangeAnimator()
        testPageButton.setOnClickListener {
            testPageButton.showProgress {
                buttonTextRes = R.string.testing
                progressColorRes = R.color.white
            }
            bgScope.launch {
                try {
                    testPrint(activity, activity.proto(), activity.mode(), activity.useCase, activity.settingsStagingArea)

                    activity.runOnUiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@runOnUiThread
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.test_success).create().show()
                    }
                } catch (e: PrintException) {
                    Sentry.captureException(e)
                    activity.runOnUiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@runOnUiThread
                        MaterialAlertDialogBuilder(requireContext()).setMessage(e.message).create().show()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    Sentry.captureException(e)
                    activity.runOnUiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@runOnUiThread
                        MaterialAlertDialogBuilder(requireContext()).setMessage(e.toString()).create().show()
                    }
                } finally {
                    activity.runOnUiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@runOnUiThread
                        testPageButton.hideProgress(R.string.button_label_test)
                    }
                }

            }
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            activity.save()
            activity.finish()
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolSettings(true)
    }
}
