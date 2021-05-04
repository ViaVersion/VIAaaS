package com.viaversion.aas.protocol.id47toid5.type

import com.viaversion.viaversion.api.type.PartialType
import io.netty.buffer.ByteBuf

class CustomIntType(amount: Int) : PartialType<IntArray, Int>(amount, IntArray::class.java) {
    override fun read(p0: ByteBuf, p1: Int): IntArray {
        return IntArray(p1) { p0.readInt() }
    }

    override fun write(p0: ByteBuf, p1: Int, p2: IntArray) {
        p2.forEach { p0.writeInt(it) }
    }
}
