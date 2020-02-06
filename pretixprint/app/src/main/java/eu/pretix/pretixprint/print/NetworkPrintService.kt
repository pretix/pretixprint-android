package eu.pretix.pretixprint.print

import android.content.Context
import android.graphics.Bitmap
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.renderers.pdfToBitmap
import eu.pretix.pretixprint.renderers.renderFileTo
import eu.pretix.pretixprint.socket.FGLNetworkPrinter
import eu.pretix.pretixprint.socket.PlaintextNetworkPrinter
import eu.pretix.pretixprint.socket.SLCSNetworkPrinter
import java8.util.concurrent.CompletableFuture
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors

class NetworkPrintService(context: Context, type: String = "ticket", mode: String = "CUPS/IPP") : PrintServiceTransport(context, type) {
    val mode = mode
    protected var threadPool = Executors.newCachedThreadPool()

    inline fun <reified T> renderPages(protocol: ByteProtocol<T>, file: File, d: Float, numPages: Int): List<CompletableFuture<ByteArray>> {
        val futures = mutableListOf<CompletableFuture<ByteArray>>()
        var previousBmpFuture: CompletableFuture<T>? = null

        for (i in 0 until numPages) {
            val bmpFuture = CompletableFuture<T>()
            val byteFuture = CompletableFuture<ByteArray>()

            if (previousBmpFuture != null) {
                previousBmpFuture.thenApplyAsync {
                    renderFileTo<T>(file, i, d, bmpFuture, T::class.java)
                }
                bmpFuture.thenCombineAsync(previousBmpFuture) { bmp1, bmp2 ->
                    byteFuture.complete(protocol.convertPageToBytes(bmp1, i == numPages - 1, bmp2))
                }
            } else {
                threadPool.submit {
                    renderFileTo<T>(file, i, d, bmpFuture, T::class.java)
                }
                bmpFuture.thenApplyAsync {
                    byteFuture.complete(protocol.convertPageToBytes(it, i == numPages - 1, null))
                }
            }

            previousBmpFuture = bmpFuture
            futures.add(byteFuture)
        }
        return futures
    }


    override fun print(tmpfile: File, numPages: Int) {
        val prefs = context.defaultSharedPreferences
        val serverAddr = InetAddress.getByName(prefs.getString("hardware_${type}printer_ip", "127.0.0.1"))
        val port = Integer.valueOf(prefs.getString("hardware_${type}printer_port", "9100"))
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            if (mode == "FGL") {
                val proto = FGL()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "SLCS") {
                val proto = SLCS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "CUPS/IPP") {
                // TODO: needs to move elsewhere
            } else if (mode == "ESC/POS") {
                val proto = ESCPOS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            }
        } catch (e: PrintError) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}