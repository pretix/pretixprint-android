package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.util.Log
import androidx.fragment.app.Fragment
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.FGLSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min


class FGL : StreamByteProtocol<Bitmap> {
    override val identifier = "FGL"
    override val nameResource = R.string.protocol_fgl
    override val defaultDPI = 200
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return (type !is SunmiInternalConnection) && (type !is IMinInternalConnection)
    }

    enum class Ticketpath(val id: Int) {
        Path1(1),
        Path2(2)
    }

    // With diffRendering=true, in a multi-page file we'd only send the pixels that changed compared
    // to the last page. Could be faster,  but just causes more problems with most printers.
    val diffRendering = false

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        val w = img.width
        val h = img.height
        val stepsize = 100
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)
        val previousPixels = IntArray(w * h)
        if (previousPage != null && diffRendering) {
            previousPage.getPixels(previousPixels, 0, w, 0, 0, w, h)
        }

        val path = conf.get("hardware_${type}printer_path") ?: "1"
        ostream.write("<P$path>".toByteArray())

        for (yoffset in 0 until h step 8) {
            for (xoffset in 0 until w step stepsize) {
                val row = ByteArray(stepsize)
                var anyChanged = false
                for (x in xoffset..min(xoffset + stepsize - 1, w - 1)) {
                    var col = 0
                    for (j in 0..7) {
                        val px = pixels[min(x + w * (yoffset + j), pixels.size - 1)]
                        val pxIsBlack = (px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128) // A > 128 && (R < 128 || G < 128 || B < 128)
                        if (!anyChanged) {
                            val previousPx = previousPixels[min(x + w * (yoffset + j), pixels.size - 1)]
                            val previousPxIsBlack = (previousPx shr 24) and 0xff > 128 && ((previousPx shr 16) and 0xff < 128 || (previousPx shr 8) and 0xff < 128 || previousPx and 0xff < 128) // A > 128 && (R < 128 || G < 128 || B < 128)
                            if (pxIsBlack != previousPxIsBlack) {
                                anyChanged = true
                            }
                        }
                        if (pxIsBlack) {
                            col = col or (1 shl (7 - j))
                        }
                    }
                    row[x - xoffset] = col.toByte()
                }
                if (anyChanged) {
                    ostream.write("<RC${yoffset},${xoffset}><G${stepsize}>".toByteArray())
                    ostream.write(row)
                    ostream.write("\n".toByteArray())
                }
            }
        }
        if (isLastPage) {
            ostream.write("<z>\n".toByteArray())
        } else if (diffRendering) {
            ostream.write("<r>\n".toByteArray())
        } else {
            ostream.write("<q>\n".toByteArray())
        }
        return ostream.toByteArray()
    }

    override fun send(
        pages: List<CompletableFuture<ByteArray>>,
        istream: InputStream,
        ostream: OutputStream,
        conf: Map<String, String>,
        type: String,
        waitAfterPage: Long
    ) {
        while (istream.available() > 0) {
            // Flush buffer of error codes from previous prints
            istream.read()
        }
        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(page)
            ostream.flush()
            Log.i("PrintService", "[$type] Page sent, waiting for printer to complete")
            val loopStarted = System.currentTimeMillis()
            wait@ while (true) {
                val r = istream.read()
                when (r) {
                    -1 -> Thread.sleep(10)
                    0 -> Thread.sleep(10)
                    1 -> break@wait  // reject bin warning
                    2 -> throw PrintError("Reject bin error")
                    3 -> throw PrintError("Paper jam (path 1)")
                    4 -> throw PrintError("Paper jam (path 2)")
                    5 -> break@wait // test button ticket ack
                    6 -> break@wait // ticket ack
                    7 -> throw PrintError("Wrong file identifier during update")
                    8 -> throw PrintError("Invalid checksum")
                    9 -> break@wait // valid checksum
                    10 -> throw PrintError("Out of paper (path 1)")
                    11 -> throw PrintError("Out of paper (path 2)")
                    12 -> break@wait // paper loaded path 1
                    13 -> break@wait // paper loaded path 2
                    14 -> throw PrintError("Escrow jam")
                    15 -> break@wait // low paper
                    16 -> throw PrintError("Out of paper")
                    17 -> Thread.sleep(10) // x-on
                    18 -> break@wait // power on
                    19 -> Thread.sleep(10) // x-off = busy
                    20 -> throw PrintError("Bad flash memory")
                    21 -> throw PrintError("Illegal print command")
                    22 -> break@wait // ribbon low
                    23 -> throw PrintError("Ribbon out")
                    24 -> throw PrintError("Paper jam")
                    25 -> throw PrintError("Illegal data")
                    26 -> throw PrintError("Powerup problem")
                    28 -> throw PrintError("Downloading error")
                    29 -> throw PrintError("Cutter jam")
                    30 -> throw PrintError("Stuck ticket")
                    31 -> throw PrintError("Cutter jam (path 2)")
                    else -> throw PrintError("Invalid status response: $r")
                }
                if (System.currentTimeMillis() - loopStarted > 15000) {
                    throw PrintError("Response timeout")
                }
            }
        }
        Log.i("PrintService", "[$type] Job done, sleep")
        Thread.sleep(waitAfterPage)
    }

    override fun createSettingsFragment(): SetupFragment {
        return FGLSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}