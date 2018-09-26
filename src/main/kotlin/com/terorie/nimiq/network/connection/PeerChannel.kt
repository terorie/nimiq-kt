package com.terorie.nimiq.network.connection

import com.terorie.nimiq.network.DataChannel
import com.terorie.nimiq.network.message.InvVector
import com.terorie.nimiq.network.message.InventoryMessage
import com.terorie.nimiq.network.message.Message

@ExperimentalUnsignedTypes
class PeerChannel {

    fun expectMessage(
            types: Array<Message.Type>,
            onTimeout: () -> Unit,
            msgTimeout: Int = DataChannel.MESSAGE_TIMEOUT,
            chunkTimeout: Int = DataChannel.CHUNK_TIMEOUT) {
        conn.expectMessage(types, onTimeout, msgTimeout, chunkTimeout)
    }

    fun getHeader(vectors: List<InvVector>): Boolean =
        send(InventoryMessage(Message.Type.GET_HEADER, vectors))

    private fun send(msg: Message) =
        conn.send(msg.serialize())

}
