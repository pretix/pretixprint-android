package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import java.io.File
import java.net.InetAddress
import java.net.Socket

class PlaintextNetworkPrinter(ip: String, port: Int, dpi: Int) : SocketNetworkPrinter(ip, port, dpi) {
    override fun convertPageToBytes(img: Bitmap): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun send(file: File) {
        val serverAddr = InetAddress.getByName(ip)
        val socket = Socket(serverAddr, port)

        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            ostream.write(file.readBytes())
            ostream.flush()
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }

    }

}