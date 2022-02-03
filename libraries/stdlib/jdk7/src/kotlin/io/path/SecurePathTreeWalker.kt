/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.io.path

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes

// TODO: should SecurityException be caught and passed to onFail?
//  Or maybe IOException is enough?
//  Currently all exceptions are caught.
internal class SecurePathTreeWalker private constructor(
    private val linkOptions: Array<LinkOption>,
    private var onFile: ((Path) -> Unit)?,
    private var onEnter: ((Path) -> Unit)?,
    private var onLeave: ((Path) -> Unit)?,
    private var onFail: ((f: Path, e: Throwable) -> Unit)?,
) {
    constructor(followLinks: Boolean) : this(
        linkOptions = LinkFollowing.toOptions(followLinks),
        onFile = null,
        onEnter = null,
        onLeave = null,
        onFail = null
    )

    fun onFile(function: (Path) -> Unit): SecurePathTreeWalker {
        return this.apply { onFile = function }
    }

    fun onEnterDirectory(function: (Path) -> Unit): SecurePathTreeWalker {
        return this.apply { onEnter = function }
    }

    fun onLeaveDirectory(function: (Path) -> Unit): SecurePathTreeWalker {
        return this.apply { onLeave = function }
    }

    fun onFail(function: (Path, Throwable) -> Unit): SecurePathTreeWalker {
        return this.apply { onFail = function }
    }

    private fun tryInvoke(function: ((Path) -> Unit)?, path: Path) {
        try {
            function?.invoke(path)
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception) ?: throw exception
        }
    }

    private val stack = mutableListOf<Pair<Path, Any?>>()

    private fun createsLoop(path: Path, key: Any?): Boolean {
        for ((ancestorPath, ancestorKey) in stack) {
            if (ancestorKey != null && key != null) {
                if (ancestorKey == key)
                    return true
            } else {
                try {
                    if (ancestorPath.isSameFileAs(path))
                        return true
                } catch (_: IOException) { // ignore
                } catch (_: SecurityException) { // ignore
                }
            }
        }

        return false
    }

    private fun beforeWalkingEntries(path: Path, key: Any?) {
        if (createsLoop(path, key))
            throw FileSystemLoopException(path.toString())

        stack.add(Pair(path, key))
        tryInvoke(onEnter, path)
    }

    private fun afterWalkingEntries(path: Path) {
        tryInvoke(onLeave, path)
        stack.removeLast()
    }

    // TODO: Guava opens parent directory stream to make sure all checks are done in a secure environment.
    fun walk(path: Path): Unit {
        if (!path.isDirectory(*linkOptions)) {
            if (path.exists(LinkOption.NOFOLLOW_LINKS)) {
                tryInvoke(onFile, path)
            }
            return
        }

        val key = path.readAttributes<BasicFileAttributes>(*linkOptions).fileKey()
        beforeWalkingEntries(path, key)

        try {
            // test start symlink to a directory
            // behavior not documented for symlinks
            Files.newDirectoryStream(path).use { directoryStream ->
                if (directoryStream is SecureDirectoryStream) {
                    directoryStream.walkEntries()
                } else {
                    directoryStream.walkEntries()
                }
            }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        afterWalkingEntries(path)

        /* catch (_: NotDirectoryException) {
            // test a file with limited read access
            // test a non-existent file
            // test a non-existent directory
            if (start.exists(LinkOption.NOFOLLOW_LINKS)) {
                onFile?.invoke(start)
            }
        }*/
    }

    // secure walk

    private fun SecureDirectoryStream<Path>.walkEntries() {
        for (entry in this) {
            val attributes = directoryAttributesOrNull(entry)

            if (attributes != null) {
                enterDirectory(entry, attributes.fileKey())
            } else {
                tryInvoke(onFile, entry)
            }
        }
    }

    private fun SecureDirectoryStream<Path>.enterDirectory(path: Path, key: Any?) {
        beforeWalkingEntries(path, key)

        try {
            this.newDirectoryStream(path).use { it.walkEntries() }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        afterWalkingEntries(path)
    }

    /** If the given [path] is a directory, returns its attributes. Returns `null` otherwise. */
    private fun SecureDirectoryStream<Path>.directoryAttributesOrNull(path: Path): BasicFileAttributes? {
        try {
            return getFileAttributeView(path, BasicFileAttributeView::class.java, *linkOptions).readAttributes().takeIf { it.isDirectory }
        } catch (exception: IOException) {
            // ignore
            return null
        }
    }

    // insecure walk

    private fun DirectoryStream<Path>.walkEntries() {
        for (entry in this) {
            val attributes = directoryAttributesOrNull(entry)

            if (attributes != null) {
                enterDirectory(entry, attributes.fileKey())
            } else {
                tryInvoke(onFile, entry)
            }
        }
    }

    private fun enterDirectory(path: Path, key: Any?) {
        beforeWalkingEntries(path, key)

        try {
            Files.newDirectoryStream(path).use { it.walkEntries() }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        afterWalkingEntries(path)
    }

    /** If the given [path] is a directory, returns its attributes. Returns `null` otherwise. */
    private fun directoryAttributesOrNull(path: Path): BasicFileAttributes? {
        try {
            return path.readAttributes<BasicFileAttributes>(*linkOptions).takeIf { it.isDirectory }
        } catch (exception: IOException) {
            // ignore
            return null
        }
    }
}