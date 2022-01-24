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
private class PathTreeWalk(
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

    private fun dfsIterator() = iterator<Path> {
        if (!start.isDirectory(*linkOptions)) {
            if (start.exists(LinkOption.NOFOLLOW_LINKS)) yield(start)
            return@iterator
        }

        // Stack of directory iterators, beginning from the start directory
        val iterators = ArrayList<Iterator<Path>>()

        if (includeDirectories) yield(start)
        iterators.add(start.directoryEntriesIterator())

        while (iterators.isNotEmpty()) {
            val topIterator = iterators.last()
            if (!topIterator.hasNext()) {
                // There is nothing more on the top of the stack, go back
                iterators.removeLast()
                continue
            }

            val path = topIterator.next()

            // Check that file/directory matches the filter
            if (!path.isDirectory(*linkOptions)) {
                // Proceed to a simple file
                yield(path)
            } else {
                if (includeDirectories) yield(path)
                // Proceed to a subdirectory
                iterators.add(path.directoryEntriesIterator())
            }
        }
    }

    private fun Path.directoryEntriesIterator() = listDirectoryEntries().iterator()

    private fun bfsIterator() = iterator<Path> {
        if (!start.isDirectory(*linkOptions)) {
            if (start.exists(LinkOption.NOFOLLOW_LINKS)) yield(start)
            return@iterator
        }

        // Queue of entries to be visited.
        val queue = ArrayDeque<Path>()
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            // TODO: the path may have been deleted using deleteRecursively applied to the parent path
            val path = queue.removeFirst()

            if (!path.isDirectory(*linkOptions)) {
                yield(path)
            } else {
                if (includeDirectories) yield(path)
                queue.addAll(path.listDirectoryEntries()) // Don't catch IOExceptions
            }
        }
    }
}

/**
 * Returns a sequence for visiting this directory and all its content.
 *
 * By default, only files are visited, in depth-first search order, and symbolic links are not followed.
 * The combination of [options] overrides the default behavior. See [PathWalkOption].
 *
 * If the file located by this path does not exist, an empty sequence is returned.
 * if the file located by this path is not a directory, a sequence containing only this path is returned.
 */
public fun Path.walk(vararg options: PathWalkOption): Sequence<Path> = PathTreeWalk(this, options)

/**
 * Visits this directory and all its content with the specified [visitor].
 *
 * @param visitor the [FileVisitor] that receives callbacks.
 * @param maxDepth the maximum depth of a directory tree to traverse. By default, there is no limit.
 * @param followLinks specifies whether to follow symbolic links, `false` by default.
 */
public fun Path.visitFileTree(visitor: FileVisitor<Path>, maxDepth: Int = Int.MAX_VALUE, followLinks: Boolean = false): Unit {
    val options = if (followLinks) setOf(FileVisitOption.FOLLOW_LINKS) else setOf()
    visitFileTree(visitor, maxDepth, options)
}

/**
 * Visits this directory and all its content with the specified [visitor].
 *
 * @param visitor the [FileVisitor] that receives callbacks.
 * @param maxDepth the maximum depth of a directory tree to traverse.
 * @param options the behavior to comply during directory tree traversal. See [FileVisitOption].
 */
public fun Path.visitFileTree(visitor: FileVisitor<Path>, maxDepth: Int, options: Set<FileVisitOption>): Unit {
    Files.walkFileTree(this, options, maxDepth, visitor)
}
