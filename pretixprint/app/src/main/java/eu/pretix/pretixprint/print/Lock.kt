package eu.pretix.pretixprint.print

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class LockManager() {
    private val lockMap = mutableMapOf<String, ReentrantLock>()

    fun <T> withLock(name: String, action: (() -> T)) {
        val lock = lockMap.getOrPut(name) { ReentrantLock() }
        lock.withLock {
            action()
        }
    }
}


val lockManager = LockManager()