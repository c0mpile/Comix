package com.c0mpile.comix.ui.reader

import android.graphics.Bitmap

/**
 * Thread-safe, bounded LRU cache for decoded comic page bitmaps.
 *
 * Uses allocation byte count to enforce a memory budget, and provides selective eviction
 * for non-visible pages during memory recovery.
 */
class ReaderPageCache(
    private val maxSizeBytes: Int = calculateDefaultMaxSize()
) {

    private val lock = Any()
    private val map = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)
    private var currentByteSize = 0L

    fun get(pageIndex: Int): Bitmap? = synchronized(lock) {
        map[pageIndex]
    }

    fun put(pageIndex: Int, bitmap: Bitmap): Unit = synchronized(lock) {
        val previous = map.put(pageIndex, bitmap)
        currentByteSize += getBitmapSize(bitmap)
        if (previous != null) {
            currentByteSize -= getBitmapSize(previous)
        }
        trimToSize(maxSizeBytes)
    }

    fun remove(pageIndex: Int): Bitmap? = synchronized(lock) {
        val removed = map.remove(pageIndex)
        if (removed != null) {
            currentByteSize -= getBitmapSize(removed)
        }
        removed
    }

    fun evictNonVisible(visiblePages: Set<Int>): Unit = synchronized(lock) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in visiblePages) {
                currentByteSize -= getBitmapSize(entry.value)
                iterator.remove()
            }
        }
    }

    fun clear(): Unit = synchronized(lock) {
        map.clear()
        currentByteSize = 0L
    }

    fun size(): Int = synchronized(lock) {
        map.size
    }

    fun byteCount(): Long = synchronized(lock) {
        currentByteSize
    }

    private fun trimToSize(targetMax: Int) {
        val iterator = map.entries.iterator()
        while (currentByteSize > targetMax && iterator.hasNext()) {
            val entry = iterator.next()
            currentByteSize -= getBitmapSize(entry.value)
            iterator.remove()
        }
    }

    private fun getBitmapSize(bitmap: Bitmap): Int {
        return try {
            bitmap.allocationByteCount
        } catch (_: Throwable) {
            // Fallback for JVM unit tests where Bitmap methods may be mocked
            1024
        }
    }

    companion object {
        fun calculateDefaultMaxSize(): Int {
            val maxMem = Runtime.getRuntime().maxMemory()
            val budget = minOf(maxMem / 6L, 64L * 1024L * 1024L)
            return budget.coerceIn(8L * 1024L * 1024L, 64L * 1024L * 1024L).toInt()
        }
    }
}
