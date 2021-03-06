package bitcoin.wallet.kit.transactions.builder

import bitcoin.wallet.kit.RealmFactoryMock
import bitcoin.wallet.kit.core.hexStringToByteArray
import bitcoin.wallet.kit.core.toHexString
import bitcoin.wallet.kit.hdwallet.PublicKey
import bitcoin.wallet.kit.managers.SelectedUnspentOutputInfo
import bitcoin.wallet.kit.managers.UnspentOutputProvider
import bitcoin.wallet.kit.managers.UnspentOutputSelector
import bitcoin.wallet.kit.models.Transaction
import bitcoin.wallet.kit.network.NetworkParameters
import bitcoin.wallet.kit.scripts.ScriptBuilder
import bitcoin.wallet.kit.scripts.ScriptType
import bitcoin.wallet.kit.transactions.TransactionSizeCalculator
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.whenever
import helpers.Fixtures
import io.realm.Realm
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TransactionBuilderTest {

    private val factory = RealmFactoryMock()
    private lateinit var realm: Realm

    private val networkParameters = mock(NetworkParameters::class.java)
    private val unspentOutputSelector = mock(UnspentOutputSelector::class.java)
    private val unspentOutputProvider = mock(UnspentOutputProvider::class.java)
    private val scriptBuilder = mock(ScriptBuilder::class.java)
    private val transactionSizeCalculator = mock(TransactionSizeCalculator::class.java)
    private val inputSigner = mock(InputSigner::class.java)


    private lateinit var previousTransaction: Transaction
    private lateinit var unspentOutputs: SelectedUnspentOutputInfo

    private val transactionBuilder = TransactionBuilder(networkParameters, unspentOutputSelector, unspentOutputProvider, scriptBuilder, transactionSizeCalculator, inputSigner)

    private val txValue = 93_417_732
    private val toAddressP2PKH = "mmLB5DvGbsb4krT9PJ7WrKmv8DkyvNx1ne"
    private val toAddressP2SH = "2MyQWMrsLsqAMSUeusduAzN6pWuH2V27ykE"
    private val feeRate = 5406
    private val changePubKey = PublicKey().apply {
        publicKeyHash = "563e1365e6567bb0115a5158bfc94fe834067fd6".hexStringToByteArray()
    }
    private val fee = 1_032_655
    private val unlockingScript = "473044022018f03676d057a3cb350d9778697ff61da47b813c82fe9fb0f2ea87b231fb865b02200706f5cbbc5ebae6f7bd77e346767bce11c8476aea607671d7321e86a3186ec1012102ce0ef85579f055e2184c935e75e71458db8c4b759cd455b0aa5d91761794eef0".hexStringToByteArray()

    @Before
    fun setUp() {
        realm = factory.realmFactory.realm
        realm.beginTransaction()
        realm.deleteAll()

        previousTransaction = realm.copyToRealm(Fixtures.transactionP2PKH)

        realm.commitTransaction()

        whenever(networkParameters.addressVersion).thenReturn(111)
        whenever(networkParameters.addressScriptVersion).thenReturn(196)

        unspentOutputs = SelectedUnspentOutputInfo(listOf(previousTransaction.outputs[0]!!), previousTransaction.outputs[0]!!.value, fee)

        whenever(unspentOutputProvider.allUnspentOutputs()).thenReturn(unspentOutputs.outputs)
        whenever(unspentOutputSelector.select(any(), any(), any(), any(), any())).thenReturn(unspentOutputs)
        whenever(transactionSizeCalculator.outputSize(any())).thenReturn(34)

        //receive address locking script P2PKH
        whenever(scriptBuilder.lockingScript(argThat { hash.contentEquals("3fc6d8a8215dd60e42a3916c4def39f40d322e29".hexStringToByteArray()) })).thenReturn("76a91437a9bfe84d9e4883ace248509bbf14c9d72af01788ac".hexStringToByteArray())
        //receive address locking script P2SH
        whenever(scriptBuilder.lockingScript(argThat { hash.contentEquals("43922a3f1dc4569f9eccce9a71549d5acabbc0ca".hexStringToByteArray()) })).thenReturn("76a91437a9bfe84d9e4883ace248509bbf14c9d72af01788ac".hexStringToByteArray())
        //change address locking script
        whenever(scriptBuilder.lockingScript(argThat { hash.contentEquals("563e1365e6567bb0115a5158bfc94fe834067fd6".hexStringToByteArray()) })).thenReturn("76a914d1997b4cc28ae0e432461479b5e89106f9d4eef488ac".hexStringToByteArray())

        whenever(inputSigner.sigScriptData(any(), any())).thenReturn(listOf())
        whenever(scriptBuilder.unlockingScript(any())).thenReturn(unlockingScript)
    }

    @Test
    fun buildTransaction_P2PKH_SenderPay() {
        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2PKH, feeRate, true, changePubKey)

        assertTrue(transaction.isMine)
        assertEquals(Transaction.Status.NEW, transaction.status)

        assertEquals(1, transaction.inputs.size)
        assertEquals(unspentOutputs.outputs[0], transaction.inputs[0]?.previousOutput)

        assertEquals(2, transaction.outputs.size)

        assertEquals(toAddressP2PKH, transaction.outputs[0]?.address)
        assertEquals(txValue.toLong(), transaction.outputs[0]?.value)

        assertEquals(changePubKey.publicKeyHash.toHexString(), transaction.outputs[1]?.keyHash?.toHexString())
        assertEquals(unspentOutputs.outputs[0].value - txValue.toLong() - fee, transaction.outputs[1]?.value)
    }

    @Test
    fun buildTransaction_P2PKH_ReceiverPay() {
        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2PKH, feeRate, false, changePubKey)

        assertTrue(transaction.isMine)
        assertEquals(Transaction.Status.NEW, transaction.status)

        assertEquals(1, transaction.inputs.size)
        assertEquals(unspentOutputs.outputs[0], transaction.inputs[0]?.previousOutput)

        assertEquals(2, transaction.outputs.size)

        assertEquals(toAddressP2PKH, transaction.outputs[0]?.address)
        assertEquals((txValue - fee).toLong(), transaction.outputs[0]?.value)

        assertEquals(changePubKey.publicKeyHash.toHexString(), transaction.outputs[1]?.keyHash?.toHexString())
        assertEquals(unspentOutputs.outputs[0].value - txValue, transaction.outputs[1]?.value)
    }

    @Test
    fun buildTransaction_P2SH() {
        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2SH, feeRate, false, changePubKey)

        assertTrue(transaction.isMine)
        assertEquals(Transaction.Status.NEW, transaction.status)

        assertEquals(1, transaction.inputs.size)
        assertEquals(unspentOutputs.outputs[0], transaction.inputs[0]?.previousOutput)

        assertEquals(2, transaction.outputs.size)

        assertEquals(toAddressP2SH, transaction.outputs[0]?.address)
        assertEquals((txValue - fee).toLong(), transaction.outputs[0]?.value)

        assertEquals(changePubKey.publicKeyHash.toHexString(), transaction.outputs[1]?.keyHash?.toHexString())
        assertEquals(unspentOutputs.outputs[0].value - txValue, transaction.outputs[1]?.value)
    }

    @Test
    fun buildTransaction_WithoutChangeOutput() {
        val txValue = unspentOutputs.outputs[0].value.toInt()

        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2PKH, feeRate, false, changePubKey)

        assertEquals(1, transaction.inputs.size)
        assertEquals(unspentOutputs.outputs[0], transaction.inputs[0]?.previousOutput)

        assertEquals(1, transaction.outputs.size)
        assertEquals(toAddressP2PKH, transaction.outputs[0]?.address)
        assertEquals((txValue - fee).toLong(), transaction.outputs[0]?.value)
    }

    @Test
    fun buildTransaction_ChangeNotAddedForDust() {
        val txValue = unspentOutputs.outputs[0].value.toInt() - transactionSizeCalculator.outputSize(scripType = ScriptType.P2PKH) * feeRate

        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2PKH, feeRate, false, changePubKey)

        assertEquals(1, transaction.inputs.size)
        assertEquals(unspentOutputs.outputs[0], transaction.inputs[0]?.previousOutput)

        assertEquals(1, transaction.outputs.size)
        assertEquals(toAddressP2PKH, transaction.outputs[0]?.address)
        assertEquals((txValue - fee).toLong(), transaction.outputs[0]?.value)
    }

    @Test
    fun buildTransaction_InputsSigned() {
        val transaction = transactionBuilder.buildTransaction(txValue, toAddressP2PKH, feeRate, false, changePubKey)

        assertEquals(unlockingScript.toHexString(), transaction.inputs[0]?.sigScript?.toHexString())
    }

    @Test
    fun fee() {
        val unspentOutputs = SelectedUnspentOutputInfo(listOf(), 11_805_400, 112_800)
        whenever(unspentOutputSelector.select(any(), any(), any(), any(), any())).thenReturn(unspentOutputs)
        val fee = transactionBuilder.fee(10_782_000, 600, true, toAddressP2PKH)

        assertEquals(133_200, fee)
    }

}
