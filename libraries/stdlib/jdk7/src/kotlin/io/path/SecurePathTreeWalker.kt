/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.io.path

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView

// TODO: should SecurityException be caught and passed to onFail?
//  Or maybe IOException is enough?
//  Currently all exceptions are caught.
internal class SecurePathTreeWalker private constructor(
    private var onFile: ((Path) -> Unit)?,
    private var onEnter: ((Path) -> Boolean)?,
    private var onLeave: ((Path) -> Unit)?,
    private var onFail: ((f: Path, e: Throwable) -> Unit)?,
) {
    constructor() : this(
        onFile = null,
        onEnter = null,
        onLeave = null,
        onFail = null
    )

    fun onFile(function: (Path) -> Unit): SecurePathTreeWalker {
        return this.apply { onFile = function }
    }

    fun onEnterDirectory(function: (Path) -> Boolean): SecurePathTreeWalker {
        return this.apply { onEnter = function }
    }

    fun onLeaveDirectory(function: (Path) -> Unit): SecurePathTreeWalker {
        return this.apply { onLeave = function }
    }

    fun onFail(function: (Path, Throwable) -> Unit): SecurePathTreeWalker {
        return this.apply { onFail = function }
    }

    private fun skipDirectory(path: Path): Boolean {
        try {
            if (onEnter?.invoke(path) == false) return true
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception) ?: throw exception
        }
        return false
    }

    private fun tryInvoke(function: ((Path) -> Unit)?, path: Path) {
        try {
            function?.invoke(path)
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception) ?: throw exception
        }
    }

    fun walk(path: Path, followLinks: Boolean): Unit {
        val linkOptions = LinkFollowing.toOptions(followLinks)

        if (path.isDirectory(*linkOptions)) {
            if (skipDirectory(path)) {
                return
            }
        } else {
            if (path.exists(LinkOption.NOFOLLOW_LINKS)) {
                tryInvoke(onFile, path)
            }
            return
        }

        try {
            // test start symlink to a directory
            // behavior not documented for symlinks
            Files.newDirectoryStream(path).use { directoryStream ->
                if (directoryStream is SecureDirectoryStream) {
                    directoryStream.walkEntries(linkOptions)
                } else {
                    directoryStream.walkEntries(linkOptions)
                }
            }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        tryInvoke(onLeave, path)

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

    private fun SecureDirectoryStream<Path>.walkEntries(linkOptions: Array<LinkOption>) {
        for (entry in this) {
            if (isDirectory(entry, linkOptions)) {
                enterDirectory(entry, linkOptions)
            } else {
                tryInvoke(onFile, entry)
            }
        }
    }

    private fun SecureDirectoryStream<Path>.enterDirectory(path: Path, linkOptions: Array<LinkOption>) {
        if (skipDirectory(path)) {
            return
        }

        try {
            this.newDirectoryStream(path).use { it.walkEntries(linkOptions) }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        tryInvoke(onLeave, path)
    }

    private fun SecureDirectoryStream<Path>.isDirectory(path: Path, linkOptions: Array<LinkOption>): Boolean {
        return getFileAttributeView(path, BasicFileAttributeView::class.java, *linkOptions).readAttributes().isDirectory
    }

    // insecure walk

    private fun DirectoryStream<Path>.walkEntries(linkOptions: Array<LinkOption>) {
        for (entry in this) {
            if (entry.isDirectory(*linkOptions)) {
                enterDirectory(entry, linkOptions)
            } else {
                tryInvoke(onFile, entry)
            }
        }
    }

    private fun enterDirectory(path: Path, linkOptions: Array<LinkOption>) {
        if (skipDirectory(path)) {
            return
        }

        try {
            Files.newDirectoryStream(path).use { it.walkEntries(linkOptions) }
        } catch (exception: Throwable) {
            onFail?.invoke(path, exception)
        }

        tryInvoke(onLeave, path)
    }
}