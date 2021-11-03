import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class MessageType {
    VALUE,
    GAIN
}

@Serializable
data class MessageContent @OptIn(ExperimentalSerializationApi::class) constructor(
    @ProtoNumber(1)
    val value: String? = null,
    @ProtoNumber(2)
    val gain: Float? = null)

@Serializable
data class MgmMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    @ProtoNumber(1)
    val agentName: String,
    @ProtoNumber(2)
    val destination: String,
    @ProtoNumber(3)
    val messageType: MessageType,
    @ProtoNumber(4)
    val messageContent: MessageContent,
)