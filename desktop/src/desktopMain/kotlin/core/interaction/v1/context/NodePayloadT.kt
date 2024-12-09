package d.zhdanov.ccfit.nsu.core.interaction.v1.context

import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.NodeRole
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.StateMsg
import d.zhdanov.ccfit.nsu.core.interaction.v1.messages.types.SteerMsg
import core.network.core.connection.Node
import java.net.InetSocketAddress

interface NodePayloadT {
  val name: String
  val score: Int
  val node: Node
  fun handleEvent(event: SteerMsg, seq: Long)
  fun onContextObserverTerminated()
  fun shootContextState(
    state: StateMsg,
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  )

  fun getNodeRole(
    masterAddrId: Pair<InetSocketAddress, Int>,
    deputyAddrId: Pair<InetSocketAddress, Int>?
  ) = when(node.nodeId) {
    masterAddrId.second  -> NodeRole.MASTER
    deputyAddrId?.second -> NodeRole.DEPUTY
    else                 -> {
      when(node.nodeState) {
        Node.NodeState.Active  -> NodeRole.NORMAL
        Node.NodeState.Passive -> NodeRole.VIEWER
        else                   -> null
      }
    }
  }
}