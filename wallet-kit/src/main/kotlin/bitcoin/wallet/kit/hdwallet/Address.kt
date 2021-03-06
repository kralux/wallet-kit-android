package bitcoin.wallet.kit.hdwallet

import bitcoin.wallet.kit.network.NetworkParameters
import bitcoin.wallet.kit.scripts.ScriptParser
import bitcoin.wallet.kit.scripts.ScriptType
import bitcoin.walllet.kit.crypto.Base58
import bitcoin.walllet.kit.crypto.Bech32
import bitcoin.walllet.kit.exceptions.AddressFormatException
import bitcoin.walllet.kit.utils.Utils
import java.util.*

class Address {
    enum class Type {
        P2PKH,  // Pay to public key hash
        P2SH,   // Pay to script hash
        WITNESS // Pay to witness hash
    }

    var program: ByteArray? = null

    lateinit var type: Type
    lateinit var hash: ByteArray

    private val network: NetworkParameters

    val version: Int
        get() = when (type) {
            Type.P2SH -> network.addressVersion
            Type.P2PKH -> network.addressVersion
            Type.WITNESS -> hash[0].toInt() and 0xff
        }

    val scriptType: Int
        get() = when (type) {
            Type.P2PKH -> ScriptType.P2PKH
            Type.P2SH -> ScriptType.P2SH
            Type.WITNESS -> if (hash.size == ScriptParser.WITNESS_PKH_LENGTH) ScriptType.P2WPKH else ScriptType.P2WSH
        }

    constructor(type: Type, hash: ByteArray, network: NetworkParameters) {
        this.type = type
        this.hash = hash
        this.network = network

        // convert program to pubkey
        if (type == Type.WITNESS) {
            this.program = hash
            this.hash = byteArrayOf(0) + Bech32.convertBits(hash, 0, hash.size, 8, 5, true)
        }
    }

    constructor(address: String, network: NetworkParameters) {
        this.network = network

        if (isMixedCase(address))
            return fromBase58(address)

        try {
            fromBech32(address)
        } catch (e: Exception) {
            fromBase58(address)
        }
    }

    private fun fromBase58(address: String) {
        val data = Base58.decodeChecked(address)
        if (data.size != 20 + 1) {
            throw AddressFormatException("Address length is not 20 bytes")
        }

        type = getType(data[0].toInt() and 0xff)
        hash = Arrays.copyOfRange(data, 1, data.size)
    }

    private fun fromBech32(address: String) {
        val decoded = Bech32.decode(address)
        if (decoded.hrp != network.addressSegwitHrp) {
            throw AddressFormatException("Address HRP ${decoded.hrp} is not correct")
        }

        type = Type.WITNESS
        hash = decoded.data
        program = Bech32.convertBits(hash, 1, hash.size - 1, 5, 8, false)
    }

    private fun getType(version: Int): Type {
        if (version == network.addressVersion) {
            return Type.P2PKH
        } else if (version == network.addressScriptVersion) {
            return Type.P2SH
        }

        throw AddressFormatException("Address version $version is not correct")
    }

    private fun isMixedCase(address: String): Boolean {
        var lower = false
        var upper = false

        for (char in address) {
            val code = char.hashCode()
            if (code in 0x30..0x39)
                continue

            if (code and 32 > 0) {
                check(code in 0x61..0x7a)
                lower = true
            } else {
                check(code in 0x41..0x5a)
                upper = true
            }

            if (lower && upper)
                return true
        }

        return false
    }

    override fun toString(): String {
        if (type == Type.WITNESS) {
            return Bech32.encode(network.addressSegwitHrp, hash)
        }

        var addressBytes = byteArrayOf(version.toByte())
        if (type == Type.P2PKH) {
            addressBytes[0] = network.addressVersion.toByte()
        } else {
            addressBytes[0] = network.addressScriptVersion.toByte()
        }

        addressBytes += hash
        val checksum = Utils.doubleDigest(addressBytes)
        addressBytes += Arrays.copyOfRange(checksum, 0, 4)

        return Base58.encode(addressBytes)
    }
}
