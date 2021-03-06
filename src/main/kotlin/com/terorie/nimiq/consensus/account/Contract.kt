package com.terorie.nimiq.consensus.account

import com.terorie.nimiq.consensus.primitive.Satoshi
import com.terorie.nimiq.consensus.transaction.Transaction

@ExperimentalUnsignedTypes
abstract class Contract(balance: Satoshi) : Account() {

    companion object {
        fun create(type: Type, balance: Satoshi, blockHeight: UInt, tx: Transaction) = when(type) {
            Type.VESTING -> throw TODO()
            Type.HTLC -> HashTimeLockedContract.create(balance, tx)
            else -> throw IllegalArgumentException("not a contract")
        }

        fun verifyIncomingTransaction(transaction: Transaction): Boolean {
            if (transaction.recipient != transaction.getContactCreationAddress())
                return false
            return true
        }
    }

    init {
        this.balance = balance
    }

    override fun withIncomingTransaction(transaction: Transaction, blockHeight: UInt, revert: Boolean): Account {
        if (!revert && (transaction.flags and Transaction.FLAG_CONTACT_CREATION) != 0.toUByte())
            throw IllegalArgumentException("Contract already created")
        return super.withIncomingTransaction(transaction, blockHeight, revert)
    }

    fun withContractCommand(transaction: Transaction, blockHeight: UInt, revert: Boolean = false): Account {
        if (revert && (transaction.flags and Transaction.FLAG_CONTACT_CREATION) != 0.toUByte())
            return BasicAccount(balance)
        return this
    }

}