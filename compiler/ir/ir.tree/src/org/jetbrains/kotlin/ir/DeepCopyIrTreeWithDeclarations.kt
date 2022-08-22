package org.jetbrains.kotlin.ir

import org.jetbrains.kotlin.ir.util.DeepCopyIrTreeWithSymbols
import org.jetbrains.kotlin.ir.util.DeepCopySymbolRemapper
import org.jetbrains.kotlin.ir.util.DeepCopyTypeRemapper
import org.jetbrains.kotlin.ir.util.NullDescriptorsRemapper
import org.jetbrains.kotlin.ir.visitors.acceptVoid

@Suppress("UNCHECKED_CAST")
fun <T : IrElement> T.deepCopyWithVariables(): T {
    val symbolsRemapper = DeepCopySymbolRemapper(NullDescriptorsRemapper)
    acceptVoid(symbolsRemapper)

    val typesRemapper = DeepCopyTypeRemapper(symbolsRemapper)

    return this.transform(DeepCopyIrTreeWithSymbols(symbolsRemapper, typesRemapper), null) as T
}
