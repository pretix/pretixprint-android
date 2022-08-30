package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.SetupFragment

class StarPRNT : ESCPOS() {
    override val identifier = "StarPRNT"
    override val nameResource = R.string.protocol_starprnt

    override fun createSettingsFragment(): SetupFragment? {
        return null
    }
}