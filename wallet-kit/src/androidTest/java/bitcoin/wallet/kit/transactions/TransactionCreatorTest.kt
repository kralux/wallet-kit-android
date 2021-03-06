package bitcoin.wallet.kit.transactions

import bitcoin.wallet.kit.RealmFactoryMock
import bitcoin.wallet.kit.managers.AddressManager
import bitcoin.wallet.kit.models.Transaction
import bitcoin.wallet.kit.network.PeerGroup
import bitcoin.wallet.kit.transactions.builder.TransactionBuilder
import com.nhaarman.mockito_kotlin.*
import helpers.Fixtures
import io.realm.exceptions.RealmException
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TransactionCreatorTest {

    private val realmFactoryMock = RealmFactoryMock()
    private val realm = realmFactoryMock.realmFactory.realm

    private val transactionBuilder = mock(TransactionBuilder::class.java)
    private val transactionProcessor = mock(TransactionProcessor::class.java)
    private val peerGroup = mock(PeerGroup::class.java)
    private val addressManager = mock(AddressManager::class.java)

    private val transactionCreator = TransactionCreator(realmFactoryMock.realmFactory, transactionBuilder, transactionProcessor, peerGroup, addressManager)

    @Before
    fun setUp() {
        realm.beginTransaction()
        realm.deleteAll()
        realm.commitTransaction()

        whenever(transactionBuilder.buildTransaction(any(), any(), any(), any(), any(), any())).thenReturn(Fixtures.transactionP2PKH)
        whenever(transactionProcessor.enqueueRun()).then { }
        whenever(peerGroup.relay(any())).then { }
        whenever(addressManager.changePublicKey()).thenReturn(Fixtures.publicKey)
    }

    @Test
    fun create_Success() {
        transactionCreator.create("address", 10_000_000)

        val insertedTx = realm.where(Transaction::class.java).equalTo("hashHexReversed", Fixtures.transactionP2PKH.hashHexReversed).findFirst()

        assertTrue(insertedTx != null)
        verify(transactionProcessor).enqueueRun()
        verify(peerGroup).relay(argThat { hashHexReversed == Fixtures.transactionP2PKH.hashHexReversed })
    }

    @Test(expected = TransactionCreator.TransactionAlreadyExists::class)
    fun create_failWithTransactionAlreadyExists() {
        realm.executeTransaction { it.insert(Fixtures.transactionP2PKH) }

        transactionCreator.create("address", 123)
    }

    @Test
    fun create_failNoChangeAddress() {
        whenever(addressManager.changePublicKey()).thenThrow(RealmException("no item found"))

        try {
            transactionCreator.create("address", 123)
        } catch (ex: Exception) {
            assertTrue(ex is RealmException)
        }

        verify(transactionProcessor, never()).enqueueRun()
        verify(peerGroup, never()).relay(any())
    }

}

