package eu.pretix.pretixprint.ui

import eu.pretix.pretixprint.byteprotocols.LinkOS

class LinkOSSettingsFragment : FGLSettingsFragment() {
    override val proto = LinkOS()
    override val hasCutMode = false
    override val hasPath = false
}