package eu.pretix.pretixprint

import java.lang.Exception

class PrintException(override var message: String) : Exception(message)
