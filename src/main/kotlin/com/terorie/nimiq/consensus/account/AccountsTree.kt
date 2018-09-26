package com.terorie.nimiq.consensus.account

import com.terorie.nimiq.consensus.primitive.HashLight
import java.lang.IllegalStateException

@ExperimentalUnsignedTypes
class AccountsTree(private val store: AccountsTreeStore) {

    operator fun get(address: Address) =
        (store.get(address.toHex())
            as? AccountsTreeTerminal)
        ?.account

    operator fun set(address: Address, account: Account) {
        if (account.isInitial() && get(address) != null)
            return

        // Fetch the root node.
        val rootNode = store.getRootNode()
            ?: throw IllegalStateException("Corrupted store: Failed to fetch root node")

        // Insert account into the tree at address.
        val prefix = address.toHex()
        insert(rootNode, prefix, account, ArrayList())
    }

    private fun insert(node: AccountsTreeNode, prefix: String, account: Account, rootPath: ArrayList<AccountsTreeNode>) {
        var _node = node

        // Find common prefix between node and new address.
        val commonPrefix = prefix.commonPrefixWith(_node.prefix)

        // If the node prefix does not fully match the new address, split the node.
        if (commonPrefix.length != _node.prefix.length) {
            // Insert the new account node.
            val newChild = AccountsTreeTerminal(prefix, account)
            val newChildHash = newChild.hash
            store.put(newChild)

            // Insert the new parent node.
            val newParent = AccountsTreeBranch(commonPrefix)
            store.put(newParent)

            updateKeys(newParent.prefix, newParent.hash, rootPath)
        }

        // If the commonPrefix is the specified address, we have found an (existing) node
        // with the given address. Update the account.
        if (commonPrefix == prefix) {
            // XXX How does this generalize to more than one account type?
            // Special case: If the new balance is the initial balance
            // (i.e. balance=0, nonce=0), it is like the account never existed
            // in the first place. Delete the node in this case.
            if (account.isInitial()) {
                store.remove(_node)
                // We have already deleted the node, remove the subtree it was on.
                return prune(_node.prefix, rootPath)
            }

            // Update the account.
            _node = _node.withAccount(account)
            store.put(_node)

            return updateKeys(_node.prefix, _node.hash, rootPath)
        }

        // If the node prefix matches and there are address bytes left, descend into
        // the matching child node if one exists.
        if (_node is AccountsTreeBranch) {
            val childPrefix = _node.getChild(prefix)
            val childNode = store.get(childPrefix)
            if (childNode != null) {
                rootPath.add(node)
                return insert(childNode, prefix, account, rootPath)
            }
        }

        // If no matching child exists, add a new child account node to the current node.
        val newChild = AccountsTreeTerminal(prefix, account)
        store.put(newChild)

        _node as AccountsTreeBranch
        _node = _node.withChild(newChild.prefix, newChild.hash)
        store.put(_node)

        return updateKeys(node.prefix, node.hash, rootPath)
    }

    private fun prune(prefix: String, rootPath: ArrayList<AccountsTreeNode>) {
        var _prefix = prefix
        // Walk along the rootPath towards the root node starting with the
        // immediate predecessor of the node specified by 'prefix'.
        for (i in rootPath.size - 1 downTo 0) {
            var node = rootPath[i]
            node = node.withoutChild(_prefix)

            // If the node has only a single child, merge it with the next node.
            if (node.hasSingleChild && node.prefix.isNotEmpty()) {
                store.remove(node)

                node as AccountsTreeBranch
                val childPrefix = node.getFirstChild()!!

                // TODO ???
                val childNode = store.get(childPrefix)
                store.put(childNode!!)

                updateKeys(_prefix, childNode.hash, rootPath)
                return
            }
            // Otherwise, if the node has children left, update it and all keys on the
            // remaining root path. Pruning finished.
            // XXX Special case: We start with an empty root node. Don't delete it.
            else if (node.hasChildren || node.prefix.isNotEmpty()) {
                store.put(node)
                updateKeys(node.prefix, node.hash, rootPath.slice(0..i))
                return
            }

            // The node has no children left, continue pruning.
            _prefix = node.prefix
        }

        // XXX This should never be reached.
        throw IllegalStateException()
    }

    private fun updateKeys(prefix: String, nodeHash: HashLight, rootPath: ArrayList<AccountsTreeNode>) {
        var _prefix = prefix
        var _nodeHash = nodeHash
        // Walk along the rootPath towards the root node starting with the
        // immediate predecessor of the node specified by 'prefix'.
        for (i in rootPath.size - 1 downTo 0) {
            var node = rootPath[i]
            node = node.withChild(_prefix, _nodeHash)
            store.put(node)
            _prefix = node.prefix
            _nodeHash = node.hash

        }
        return
    }

}
