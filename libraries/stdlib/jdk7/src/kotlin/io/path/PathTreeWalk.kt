/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:JvmMultifileClass
@file:JvmName("PathsKt")

package kotlin.io.path

import java.nio.file.*


/**
 * An enumeration to describe possible walk options.
 * The options can be combined to get the walk order and behavior needed.
 *
 * Note that this enumeration is not exhaustive and new cases can be added in the future.
 */
public enum class PathWalkOption {
    /** Depth-first search, directory is visited BEFORE its entries */
    INCLUDE_DIRECTORIES,

    /** Breadth-first search, if combined with [INCLUDE_DIRECTORIES], directory and its siblings are visited BEFORE the directory entries */
    BFS,

    /** Symlinks are followed to the directories they point to */
    FOLLOW_LINKS
}

/**
 * This class is intended to implement different file traversal methods.
 * It allows to iterate through all files inside a given directory.
 * Iteration order of sibling files is unspecified.
 *
 * If the file path given is just a file, walker iterates only it.
 * If the file path given does not exist, walker iterates nothing, i.e. it's equivalent to an empty sequence.
 */
internal class PathTreeWalk(
    private val start: Path,
    private val options: Array<out PathWalkOption>
) : Sequence<Path> {

    private val linkOptions: Array<LinkOption>
        get() = LinkFollowing.toOptions(followLinks = options.contains(PathWalkOption.FOLLOW_LINKS))

    private val includeDirectories: Boolean
        get() = options.contains(PathWalkOption.INCLUDE_DIRECTORIES)

    private val isBFS: Boolean
        get() = options.contains(PathWalkOption.BFS)


    /** Returns an iterator walking through files. */
    override fun iterator(): Iterator<Path> = if (isBFS) bfsIterator() else dfsIterator()

    private suspend inline fun SequenceScope<Path>.yieldIfNeeded(path: Path, entriesAction: (List<Path>) -> Unit) {
        if (path.isDirectory(*linkOptions)) {
            entriesAction(path.listDirectoryEntries())
            if (includeDirectories)
                yield(path)
        } else if (path.exists(LinkOption.NOFOLLOW_LINKS)) {
            yield(path)
        }
    }

    private fun dfsIterator() = iterator<Path> {
        // Stack of directory iterators, beginning from the start directory
        val iterators = ArrayList<Iterator<Path>>()

        yieldIfNeeded(start) { iterators.add(it.iterator()) }

        while (iterators.isNotEmpty()) {
            val topIterator = iterators.last()
            if (topIterator.hasNext()) {
                val path = topIterator.next()
                yieldIfNeeded(path) { iterators.add(it.iterator()) }
            } else {
                // There is nothing more on the top of the stack, go back
                iterators.removeLast()
            }
        }
    }

    private fun bfsIterator() = iterator<Path> {
        // Queue of entries to be visited.
        val queue = ArrayDeque<Path>()
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            yieldIfNeeded(path) { queue.addAll(it) }
        }
    }
}

internal object LinkFollowing {
    private val nofollow = arrayOf(LinkOption.NOFOLLOW_LINKS)
    private val follow = emptyArray<LinkOption>()

    fun toOptions(followLinks: Boolean): Array<LinkOption> = if (followLinks) follow else nofollow
}
