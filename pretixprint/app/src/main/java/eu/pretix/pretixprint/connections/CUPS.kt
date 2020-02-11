package eu.pretix.pretixprint.connections

import android.content.Context
import eu.pretix.pretixprint.R
import java.io.File


class CUPSConnection : ConnectionType {
    override val identifier = "cups"
    override val nameResource = R.string.connection_type_cups
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}