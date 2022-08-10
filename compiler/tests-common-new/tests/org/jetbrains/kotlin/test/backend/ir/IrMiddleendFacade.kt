/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.TestServices

abstract class IrMiddleendFacade<I : ResultingArtifact.BackendInput<I>, O : ResultingArtifact.MiddleendOutput<O>>(
    testServices: TestServices,
    irBackendInputKind: BackendKind<I>,
    irMiddleendKind: MiddleendKind<O>
) :
    MiddleendFacade<I, O>(testServices, irBackendInputKind, irMiddleendKind)