package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import kotlin.math.min
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer


class FGLNetworkPrinter(ip: String, port: Int, dpi: Int) : SocketNetworkPrinter(ip, port, dpi) {

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean): ByteArray {
        val ostream = ByteArrayOutputStream()
        val w = img.width
        val h = img.height
        val stepsize = 100
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)

        //ostream.write("<CB>".toByteArray())
        for (yoffset in 0..(h - 1) step 8) {
            for (xoffset in 0..(w - 1) step stepsize) {
                val row = ByteArray(stepsize)
                var any = false
                for (x in xoffset..min(xoffset + stepsize - 1, w - 1)) {
                    var col = 0
                    for (j in 0..7) {
                        val px = pixels[min(x + w * (yoffset + j), pixels.size - 1)]
                        if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                            // A > 128 && (R < 128 || G < 128 || B < 128)
                            col = col or (1 shl (7 - j))
                        }
                    }
                    row[x - xoffset] = col.toByte()
                    if (col > 0)
                        any = true
                }
                if (any) {
                    ostream.write("<RC${yoffset},${xoffset}><G${stepsize}>".toByteArray())
                    ostream.write(row)
                    ostream.write("\n".toByteArray())
                }
            }
        }
        if (isLastPage) {
            ostream.write("<p>\n".toByteArray())
        } else {
            ostream.write("<q>\n".toByteArray())
        }
        return ostream.toByteArray()
    }

    override fun printPDF(file: File) {
        val serverAddr = InetAddress.getByName(ip)
        var d = dpi.toFloat()
        if (d < 1) {
            d = 200f  // Set default
        }
        val pages = renderPages(file, d)
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            for (p in pages) {
                ostream.write(p)
                ostream.flush()
                val loopStarted = System.currentTimeMillis()
                wait@ while (true) {
                    val r = istream.read()
                    when (r) {
                        0 -> Thread.sleep(20)
                        1 -> break@wait  // reject bin warning
                        2 -> throw FGLPrintError("Reject bin error")
                        3 -> throw FGLPrintError("Paper jam (path 1)")
                        4 -> throw FGLPrintError("Paper jam (path 2)")
                        5 -> break@wait // test button ticket ack
                        6 -> break@wait // ticket ack
                        7 -> throw FGLPrintError("Wrong file identifier during update")
                        8 -> throw FGLPrintError("Invalid checksum")
                        9 -> break@wait // valid checksum
                        10 -> throw FGLPrintError("Out of paper (path 1)")
                        11 -> throw FGLPrintError("Out of paper (path 2)")
                        12 -> break@wait // paper loaded path 1
                        13 -> break@wait // paper loaded path 2
                        14 -> throw FGLPrintError("Escrow jam")
                        15 -> break@wait // low paper
                        16 -> throw FGLPrintError("Out of paper")
                        17 -> break@wait // x-on
                        18 -> break@wait // power on
                        19 -> Thread.sleep(20) // x-off = busy
                        20 -> throw FGLPrintError("Bad flash memory")
                        21 -> throw FGLPrintError("Illegal print command")
                        22 -> break@wait // ribbon low
                        23 -> throw FGLPrintError("Ribbon out")
                        24 -> throw FGLPrintError("Paper jam")
                        25 -> throw FGLPrintError("Illegal data")
                        26 -> throw FGLPrintError("Powerup problem")
                        28 -> throw FGLPrintError("Downloading error")
                        29 -> throw FGLPrintError("Cutter jam")
                        30 -> throw FGLPrintError("Stuck ticket")
                        31 -> throw FGLPrintError("Cutter jam (path 2)")
                        else -> throw FGLPrintError("Invalid status response: $r")
                    }
                    if (System.currentTimeMillis() - loopStarted > 15000) {
                        throw FGLPrintError("Response timeout")
                    }
                }
            }
            Thread.sleep(getCooldown())
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}
