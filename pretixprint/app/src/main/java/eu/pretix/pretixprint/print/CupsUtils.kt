package eu.pretix.pretixprint.print

import org.cups4j.CupsClient
import org.cups4j.CupsPrinter
import java.net.URL


fun getPrinter(ip: String, port: String, path: String): CupsPrinter? {
    if (path.contains('/')) {
        return CupsPrinter(
                URL("http://$ip:$port/$path"),
                path.substringAfterLast("/"),
                false
        )
    } else {
        val cc = CupsClient(URL("http://$ip:$port/$path"))
        for (printer in cc.printers) {
            if (printer.name == path) {
                return printer
            }
        }
    }
    return null
}
