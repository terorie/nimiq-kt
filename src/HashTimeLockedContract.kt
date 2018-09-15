import java.io.ByteArrayInputStream
import kotlin.math.max

class HashTimeLockedContract(
        balance: Satoshi,
        val sender: Address,
        val recipient: Address,
        val hashRoot: Hash,
        val hashCount: UByte,
        val timeout: UInt,
        val totalAmount: ULong
) : Contract(balance) {

    companion object {
        fun create(balance: Satoshi, transaction: Transaction): HashTimeLockedContract {
            val s = ByteArrayInputStream(transaction.data)

            val sender = Address().apply { unserialize(s) }
            val recipient = Address().apply { unserialize(s) }
            val hashAlgorithm = s.readUByte()
            //val hashRoot = HashLight().unserialize(s)
            val hashCount = s.readUByte()
            val timeout = s.readUInt()
            val totalAmount = s.readULong()
            return HashTimeLockedContract(balance, sender, recipient, hashRoot, hashCount, timeout, totalAmount)
        }

        fun verifyIncomingTransaction(transaction: Transaction): Boolean {
            try {
                val s = ByteArrayInputStream(transaction.proof)

                s.skip(20) // Skip sender address
                s.skip(20) // Skip recipient address
                val hashAlgorithm = Hash.Algorithm.values()[s.readUByte()]
                if (hashAlgorithm == Hash.Algorithm.INVALID ||
                        hashAlgorithm == Hash.Algorithm.ARGON2D)
                    return false
                s.skip(hashAlgorithm.size.toLong())
                s.skip(1) // Skip hash count
                s.skip(4) // Skip timeout

                return Contract.verifyIncomingTransaction(transaction)
            } catch (_: Exception) {
                return false
            }
        }

        fun verifyOutgoingTransaction(transaction: Transaction): Boolean {
            try {
                val s = ByteArrayInputStream(transaction.proof)
                val type = ProofType.values()[s.readUByte()]
                when (type) {
                    ProofType.REGULAR_TRANSFER -> {
                        val hashAlgorithm = Hash.Algorithm.values()[s.readUByte()]
                        val hashDepth = s.readUByte()
                        //val hashRoot = Hash…
                        //val preImage

                        // Verify that the preImage hashed hashDepth times matches the _provided_ hashRoot.
                        //for (i in 0 until hashDepth) {
                        //    preImage = Hash.compute(preImage.array, )
                        //}

                        //if (hashRoot != preImage)
                        //    return false

                        // Signature proof of the HTLC recipient
                        if (!SignatureProof.unserialize(s).verify(null, transaction.serializeContent()))
                            return false
                    }
                    ProofType.EARLY_RESOLVE -> {
                        // Signature proof of the HTLC recipient
                        if (!SignatureProof.unserialize(s).verify(null, transaction.serializeContent()))
                            return false

                        // Signature proof of the HTLC creator
                        if (!SignatureProof.unserialize(s).verify(null, transaction.serializeContent()))
                            return false
                    }
                    ProofType.TIMEOUT_RESOLVE -> {
                        // Signature proof of the HTLC creator
                        if (!SignatureProof.unserialize(s).verify(null, transaction.serializeContent()))
                            return false
                    }
                    else ->
                        return false
                }

                // Reject overlong proof
                if (s.available() > 0)
                    return false

                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    override val type: Type
        get() = Account.Type.HTLC

    fun withOutgoingTransaction(transaction: Transaction, blockHeight: UInt, txCache: TransactionsCache, revert: Boolean = false) {
        val s = ByteArrayInputStream(transaction.proof)
        val type = ProofType.values()[s.readUByte()]
        var minCap = 0
        when (type) {
            ProofType.REGULAR_TRANSFER -> {
                // Check that the contract has not expired yet
                if (timeout < blockHeight)
                    throw IllegalArgumentException("Contract expired")

                // Check that the provided hashRoot is correct
                val hashAlgorithm = Hash.Algorithm.values()[s.readUByte()]
                val hashDepth = s.readUByte()
                val _hashRoot = Hash(hashAlgorithm).apply { unserialize(s) }
                if (_hashRoot != hashRoot)
                    throw IllegalArgumentException("Proof error")

                // Ignore the preImage
                s.skip(hashAlgorithm.size.toLong())

                // Verify that the transaction is signed by the authorized recipient
                if (!SignatureProof.unserialize(s).isSignedBy(recipient))
                    throw IllegalArgumentException("Proof error")

                minCap = max(0, (1 - (hashDepth / hashCount) * totalAmount))
            }
            ProofType.EARLY_RESOLVE -> {
                // Signature proof of the HTLC recipient
                if (!SignatureProof.unserialize(s).isSignedBy(recipient))
                    throw IllegalArgumentException("Proof error")

                // Signature proof of the HTLC creator
                if (!SignatureProof.unserialize(s).isSignedBy(sender))
                    throw IllegalArgumentException("Proof error")
            }
            ProofType.TIMEOUT_RESOLVE -> {
                if (timeout >= blockHeight)
                    throw IllegalArgumentException("Proof error")

                // Signature proof of the HTLC creator
                if (!SignatureProof.unserialize(s).isSignedBy(sender))
                    throw IllegalArgumentException("Proof error")
            }
            else ->
                throw IllegalArgumentException("Proof error")
        }

        if (!revert) {
            val newBalance = balance - transaction.value - transaction.fee
            if (newBalance < minCap)
                throw IllegalArgumentException("Balance error")
        }

        return super.withOutgoingTransaction(transaction, blockHeight, transactionsCache, revert)
    }

    fun withIncomingTransaction(transaction: Transaction,)

    enum class ProofType {
        INVALID,
        REGULAR_TRANSFER,
        EARLY_RESOLVE,
        TIMEOUT_RESOLVE
    }

}