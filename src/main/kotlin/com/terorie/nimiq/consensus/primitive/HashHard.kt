package com.terorie.nimiq.consensus.primitive

import com.terorie.nimiq.util.Blob
import com.terorie.nimiq.util.Native

class HashHard() : Blob(SIZE) {

    companion object {
        const val SIZE = 32
    }

    constructor(input: ByteArray): this() {
        Native.nimiqArgon2d(buf, input)
    }

    fun toGeneric() = Hash(Hash.Algorithm.ARGON2D)

}
