package com.convx.music.playback

/** Portable queue-edit commands implemented by each platform playback adapter. */
interface PlaybackQueueController {
    fun moveQueueItem(fromIndex: Int, toIndex: Int)

    fun removeQueueItem(index: Int)

    fun removeQueueItemsAfterCurrent()
}

/** Reorders queue indices while retaining every item exactly once. Invalid indices leave the order intact. */
fun moveQueueIndices(indices: List<Int>, fromIndex: Int, toIndex: Int): List<Int> {
    if (fromIndex !in indices.indices || toIndex !in indices.indices || fromIndex == toIndex) {
        return indices
    }

    return indices.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
