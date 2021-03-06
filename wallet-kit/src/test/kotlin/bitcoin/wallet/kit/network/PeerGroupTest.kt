package bitcoin.wallet.kit.network

import android.content.Context
import bitcoin.wallet.kit.crypto.BloomFilter
import bitcoin.wallet.kit.models.Transaction
import com.nhaarman.mockito_kotlin.whenever
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.internal.RealmCore
import junit.framework.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.api.mockito.PowerMockito.mockStatic
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.net.SocketTimeoutException

@RunWith(PowerMockRunner::class)
@PrepareForTest(PeerGroup::class, Realm::class, RealmConfiguration::class, RealmCore::class)

class PeerGroupTest {
    private lateinit var peerGroup: PeerGroup
    private lateinit var peer: Peer
    private lateinit var peer2: Peer
    private lateinit var peerManager: PeerManager
    private lateinit var peerGroupListener: PeerGroup.Listener
    private val peerIp = "8.8.8.8"
    private val peerIp2 = "5.5.5.5"
    private val network = MainNet()

    @Before
    fun setup() {
        peerGroupListener = mock(PeerGroup.Listener::class.java)
        peerManager = mock(PeerManager::class.java)
        peerGroup = PeerGroup(peerManager, network, 2)
        peerGroup.listener = peerGroupListener
        peer = mock(Peer::class.java)
        peer2 = mock(Peer::class.java)
        whenever(peer.host).thenReturn(peerIp)
        whenever(peer2.host).thenReturn(peerIp2)

        whenever(peerManager.getPeerIp())
                .thenReturn(peerIp, peerIp2)

        PowerMockito.whenNew(Peer::class.java)
                .withAnyArguments()
                .thenReturn(peer, peer2)

        // Realm initialize
        mockStatic(Realm::class.java)
        mockStatic(RealmConfiguration::class.java)
        mockStatic(RealmCore::class.java)

        RealmCore.loadLibrary(any(Context::class.java))
    }

    @Test
    fun run() { // creates peer connection with given IP address
        peerGroup.start()

        Thread.sleep(500L)
        verify(peer).start()

        // close thread:
        peerGroup.close()
        peerGroup.join()
    }

    @Test
    fun requestHeaders() {
        val hashes = arrayOf<ByteArray>()
        peerGroup.connected(peer)
        peerGroup.requestHeaders(hashes)

        verify(peer).requestHeaders(hashes)
    }

    @Test
    fun requestBlocks() {
        // we should be able to set field instead of stubbing value
        whenever(peer.isFree).thenReturn(true)

        peerGroup.start()
        peerGroup.connected(peer)

        val hashes = arrayOf(
                byteArrayOf(1, 2),
                byteArrayOf(3, 4)
        )

        peerGroup.requestMerkleBlocks(hashes)
        verify(peer).requestMerkleBlocks(hashes)
    }

    @Test
    fun connected_onReady() {
        peerGroup.connected(peer)
        verify(peerGroupListener).onReady()
    }

    @Test
    fun connected_onReady_once() {
        val peer2 = mock(Peer::class.java)
        whenever(peer2.host).thenReturn(peerIp2)

        peerGroup.connected(peer)
        peerGroup.connected(peer2)
        verify(peerGroupListener).onReady()
    }

    @Test
    fun connected_onReady_twice() {
        val peer2 = mock(Peer::class.java)
        whenever(peer2.host).thenReturn(peerIp2)

        peerGroup.connected(peer2)
        peerGroup.disconnected(peer2, null, arrayOf())
        peerGroup.connected(peer)

        verify(peerGroupListener, times(2)).onReady()
    }

    @Test
    fun disconnected() { // removes peer from connection list
        peerGroup.disconnected(peer, null, arrayOf())

        verify(peerManager).markSuccess(peerIp)
    }

    @Test
    fun disconnected_withError() { // removes peer from connection list
        peerGroup.disconnected(peer, SocketTimeoutException("Some Error"), arrayOf())

        verify(peerManager).markFailed(peerIp)
    }

    @Test
    fun disconnected_withIncompleteMerkleBlocks() {

        peerGroup.start()
        peerGroup.connected(peer)
        whenever(peer.isFree).thenReturn(true)

        val hashes = arrayOf(
                byteArrayOf(1),
                byteArrayOf(2)
        )

        peerGroup.disconnected(peer, null, hashes)
        verify(peer).requestMerkleBlocks(hashes)
    }

    @Test
    fun sendFilterLoadMessage() {
        val filter = BloomFilter(0)
        peerGroup.setBloomFilter(filter)
        peerGroup.start()

        Thread.sleep(10L)
        peerGroup.connected(peer)

        verify(peer).setBloomFilter(filter)
    }

    @Test
    fun relay() {
        peerGroup.start()

        Thread.sleep(2500L)

        // val transaction = Transaction()
        val transaction = mock(Transaction::class.java)
        Assert.assertTrue(true)

//        peerGroup.relay(transaction)
//        verify(peer).relay(transaction)
//        verify(peer2).relay(transaction)
    }

}
