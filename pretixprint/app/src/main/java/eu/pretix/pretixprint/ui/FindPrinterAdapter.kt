package eu.pretix.pretixprint.ui

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import eu.pretix.pretixprint.R

class FindPrinterAdapter(fm: FragmentManager, type: String, context: Context) : FragmentPagerAdapter(fm) {
    private val type = type
    private val context = context

    override fun getItem(position: Int): Fragment {
        val fragment : Fragment
        val args = Bundle()
        args.putString("type", type)

        when (getConnectionTechnologies()[position]) {
            R.string.bluetooth_printer -> {
                fragment = FindBluetoothPrinterFragment()
            }
            else -> {
                fragment = FindNetworkPrinterFragment()
            }
        }

        fragment.arguments = args
        return fragment
    }

    override fun getCount(): Int {
        return getConnectionTechnologies().count()
    }

    override fun getPageTitle(position: Int): CharSequence {
        return context.getString(getConnectionTechnologies()[position])
    }

    fun getPositionOf(technology: Int): Int {
        var i = 0
        for (tech in getConnectionTechnologies()) {
            if (tech == technology) {
                return i
            }
            i++
        }
        return -1
    }

    private fun getConnectionTechnologies() : List<Int> {
        return when (type) {
            "receipt" -> {
                listOf(
                        R.string.bluetooth_printer,
                        R.string.network_printer
                )
            }
            else -> {
                listOf(
                        R.string.network_printer
                )
            }
        }
    }
}