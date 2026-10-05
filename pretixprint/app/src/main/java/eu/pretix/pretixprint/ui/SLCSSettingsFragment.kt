package eu.pretix.pretixprint.ui

import eu.pretix.pretixprint.byteprotocols.SLCS

class SLCSSettingsFragment : FGLSettingsFragment() {
    override val proto = SLCS()
    override val hasCutMode = false
    override val hasPath = false
}