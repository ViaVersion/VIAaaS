package com.viaversion.aas.codec.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufHolder

class UnknownPacket(val id: Int, val content: ByteBuf) : Packet, ByteBufHolder {
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        content.writeBytes(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        byteBuf.writeBytes(content)
    }

    override fun retain(): UnknownPacket {
        content.retain()
        return this
    }

    override fun retain(increment: Int): UnknownPacket {
        content.retain(increment)
        return this
    }

    override fun touch(): UnknownPacket {
        content.touch()
        return this
    }

    override fun touch(hint: Any): UnknownPacket {
        content.touch(hint)
        return this
    }

    override fun refCnt() = content.refCnt()
    override fun release() = content.release()
    override fun release(decrement: Int) = content.release(decrement)
    override fun content(): ByteBuf = content
    override fun copy() = UnknownPacket(id, content.copy())
    override fun duplicate() = UnknownPacket(id, content.duplicate())
    override fun retainedDuplicate() = UnknownPacket(id, content.retainedDuplicate())
    override fun replace(content: ByteBuf) = UnknownPacket(id, content)
}