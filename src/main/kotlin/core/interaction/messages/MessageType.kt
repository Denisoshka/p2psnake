package d.zhdanov.ccfit.nsu.core.interaction.messages

enum class MessageType {
    PingMsg,
    SteerMsg,
    AckMsg,
    StateMsg,
    AnnouncementMsg,
    JoinMsg,
    ErrorMsg,
    RoleChangeMsg,
    DiscoverMsg,
    UnrecognisedMsg;
}