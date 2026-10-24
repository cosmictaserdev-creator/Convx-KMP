package com.convx.music.playback

import kotlinx.coroutines.flow.StateFlow

data class PlaybackQueueEntry(
    val id: String,
    val title: String,
    val artists: List<String>,
    val artworkUrl: String? = null,
)

data class PlaybackQueueSnapshot(
    val entries: List<PlaybackQueueEntry>,
    val currentIndex: Int,
) {
    companion object {
        val EMPTY = PlaybackQueueSnapshot(entries = emptyList(), currentIndex = -1)
    }
}

/** Portable queue-edit commands implemented by each platform playback adapter. */
interface PlaybackQueueController {
    val queueSnapshot: StateFlow<PlaybackQueueSnapshot>

    fun playQueueItem(index: Int)

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
