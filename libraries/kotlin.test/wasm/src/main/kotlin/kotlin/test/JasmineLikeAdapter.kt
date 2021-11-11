/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.test

import kotlin.test.FrameworkAdapter

internal external fun describe(name: String, fn: () -> Unit)
internal external fun xdescribe(name: String, fn: () -> Unit)
internal external fun it(name: String, fn: () -> Any?)
internal external fun xit(name: String, fn: () -> Any?)

/**
 * [Jasmine](https://github.com/jasmine/jasmine) adapter.
 * Also used for [Mocha](https://mochajs.org/) and [Jest](https://facebook.github.io/jest/).
 */
internal class JasmineLikeAdapter : FrameworkAdapter {
    override fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit) {
        if (ignored) {
            xdescribe(name, suiteFn)
        } else {
            describe(name, suiteFn)
        }
    }

    override fun test(name: String, ignored: Boolean, testFn: () -> Any?) {
        if (ignored) {
            xit(name, testFn)
        } else {
            it(name, testFn)
        }
    }
}