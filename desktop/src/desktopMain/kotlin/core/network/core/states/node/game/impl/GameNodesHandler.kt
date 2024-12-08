package d.zhdanov.ccfit.nsu.core.network.core.states.node.game.impl

import d.zhdanov.ccfit.nsu.SnakesProto
import d.zhdanov.ccfit.nsu.core.network.core.NetworkStateMachine
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeHandlerAlreadyInitialized
import d.zhdanov.ccfit.nsu.core.network.core.exceptions.IllegalNodeRegisterAttempt
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeContext
import d.zhdanov.ccfit.nsu.core.network.core.states.node.NodeT
import d.zhdanov.ccfit.nsu.core.network.core.states.node.lobby.impl.NetNode
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

private val Logger = KotlinLogging.logger(NetNode::class.java.name)

class GameNodesHandler(
  joinBacklog: Int,
  @Volatile var resendDelay: Long,
  @Volatile var thresholdDelay: Long,
  private val ncStateMachine: NetworkStateMachine,
) : NodeContext, Iterable<Map.Entry<InetSocketAddress, NodeT>> {
  override val launched: Boolean
    get() = nodesScope?.isActive ?: false

  @Volatile private var nodesScope: CoroutineScope? = null
  private val nodesByIp = ConcurrentHashMap<InetSocketAddress, NodeT>()
  private val deadNodeChannel = Channel<NodeT>(joinBacklog)
  private val registerNewNode = Channel<NodeT>(joinBacklog)
  private val detachNodeChannel = Channel<NodeT>(joinBacklog)
  val nextSeqNum
    get() = ncStateMachine.nextSegNum

  /**
   * @throws IllegalNodeHandlerAlreadyInitialized
   * */
  @Synchronized
  override fun launch() {
    this.nodesScope ?: throw IllegalNodeHandlerAlreadyInitialized()
    CoroutineScope(Dispatchers.Default)
      .also { nodesScope = it }
      .nodesWatcherRoutine()
  }

  @Synchronized
  override fun shutdown() {
    nodesScope?.cancel()
    nodesByIp.clear()
  }

  /**
   * Мы меняем состояние кластера в одной функции так что исполнение линейно
   */
  private fun CoroutineScope.nodesWatcherRoutine() = launch {
    try {
      while(isActive) {
        select {
          detachNodeChannel.onReceive { node ->
            ncStateMachine.handleNodeDetach(node)
          }
          deadNodeChannel.onReceive { node ->
            nodesByIp.remove(node.ipAddress)
            ncStateMachine.handleNodeDetach(node)
          }
        }
      }
    } catch(_: CancellationException) {
    } catch(e: Exception) {
      Logger.error(e) { "unexpected error" }
    }
  }

  override fun sendUnicast(
    msg: SnakesProto.GameMessage, nodeAddress: InetSocketAddress
  ) = ncStateMachine.sendUnicast(msg, nodeAddress)

  override fun registerNode(node: NodeT): NodeT {
    nodesByIp.putIfAbsent(node.ipAddress, node)?.let {
      with(it) {
        nodesScope?.startObservation()
          ?: throw IllegalNodeRegisterAttempt("nodesScope absent")
      }
      return it
    } ?: throw IllegalNodeRegisterAttempt("node already registered")
  }

  override suspend fun handleNodeTermination(
    node: NodeT
  ) = deadNodeChannel.send(node)

  override suspend fun handleNodeDetach(
    node: NodeT
  ) = detachNodeChannel.send(node)

  override fun iterator(): Iterator<Map.Entry<InetSocketAddress, NodeT>> {
    return nodesByIp.entries.iterator()
  }

  override operator fun get(ipAddress: InetSocketAddress): NodeT? {
    return nodesByIp[ipAddress]
  }
}