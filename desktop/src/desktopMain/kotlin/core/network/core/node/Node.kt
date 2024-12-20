package d.zhdanov.ccfit.nsu.core.network.core.node

import d.zhdanov.ccfit.nsu.SnakesProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.net.InetSocketAddress

interface Node<T> {
  val nodeId: Int
  val ipAddress: InetSocketAddress
  val running: Boolean
  var lastReceive: Long
  var lastSend: Long
  val nodeState: NodeState
  
  fun sendToNode(msg: SnakesProto.GameMessage)
  fun ackMessage(message: SnakesProto.GameMessage): T?
  fun addMessageForAck(message: SnakesProto.GameMessage)
  fun addAllMessageForAck(messages: Iterable<SnakesProto.GameMessage>)
  fun CoroutineScope.startObservation(): Job
  
  fun detach()
  fun shutdown()
  
  fun getUnacknowledgedMessages(): List<T>
  
  enum class NodeState {
    Active,
    Passive,
    Terminated,
  }
  
  data class MsgInfo(
    val req: SnakesProto.GameMessage,
    var lastCheck: Long
  )
  
  data class MsgInfoWithPayload(
    val req: SnakesProto.GameMessage,
    var lastCheck: Long,
    var payload: SnakesProto.GameMessage?
  )
}