package eu.pretix.pretixprint

import io.sentry.protocol.App

enum class Sensor(val sensor: Int) {
    sContinuous(sensor = 0),
    sGap(sensor = 1),
    sMark(sensor = 2);

    override fun toString(): String {
        return when (sensor) {
            sContinuous.sensor -> {
                "Continuous"
            }
            sGap.sensor -> {
                "Gap"
            }
            sMark.sensor -> {
                "Black Mark"
            }
            else -> {
                ""
            }
        }
    }
}