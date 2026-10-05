package eu.pretix.pretixprint.ui

import eu.pretix.pretixprint.byteprotocols.ESCLabel

class ESCLabelSettingsFragment : FGLSettingsFragment() {
    override val proto = ESCLabel()
    override val hasCutMode = true
    override val hasPath = true
}