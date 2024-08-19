package eu.pretix.pretixprint.print

import android.content.Context
import android.os.Build
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.StarPRNT
import eu.pretix.pretixprint.byteprotocols.ePOSPrintXML
import eu.pretix.pretixprint.byteprotocols.getProtoClass
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.CUPSConnection
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.connections.SystemConnection
import eu.pretix.pretixprint.connections.USBConnection
import io.sentry.Sentry
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

fun writeDemoPage(context: Context, settings: Map<String, String>, useCase: String, proto: String, filename: String): File {
    val file = File(context.cacheDir, filename)
    if (file.exists()) {
        file.delete()
    }
    val asset = context.assets.open(filename)
    val output = FileOutputStream(file)

    val buffer = ByteArray(1024)
    var size = asset.read(buffer)
    while (size != -1) {
        output.write(buffer, 0, size)
        size = asset.read(buffer)
    }
    asset.close()

    if (proto in listOf(ESCPOS().identifier, ePOSPrintXML().identifier, StarPRNT().identifier)) {
        // For ESC/POS, in addition to our static test page explaining printer width, we also
        // print a dynamically generated test page testing features such as text formatting and
        // QR code printing

        var dialect = ESCPOSRenderer.Companion.Dialect.values().find {
            it.name == settings.get("hardware_${useCase}printer_dialect")
        } ?: ESCPOSRenderer.Companion.Dialect.EpsonDefault

        if (proto == StarPRNT().identifier) {
            dialect = ESCPOSRenderer.Companion.Dialect.StarPRNT
        }

        val testpage = ESCPOSRenderer(dialect, JSONObject(), 32, context).renderTestPage()
        output.write(testpage)
    }

    output.close()
    return file
}

fun testPrint(context: Context, protoName: String, mode: String, useCase: String, settings: Map<String, String>) {
    val proto = getProtoClass(protoName)

    val file = writeDemoPage(context, settings, useCase, proto.identifier, proto.demopage)

    Sentry.configureScope { scope ->
        scope.setTag("printer.test", "true")
    }

    when (mode) {
        NetworkConnection().identifier -> {
            NetworkConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        BluetoothConnection().identifier -> {
            BluetoothConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        CUPSConnection().identifier -> {
            CUPSConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        SunmiInternalConnection().identifier -> {
            SunmiInternalConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        IMinInternalConnection().identifier -> {
            IMinInternalConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        USBConnection().identifier -> {
            USBConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
        SystemConnection().identifier -> {
            SystemConnection().print(file, 1, listOf(1), context, useCase, settings)
        }
    }
}