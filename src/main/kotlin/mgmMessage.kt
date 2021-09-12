import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
enum class MessageType {
    VALUE,
    GAIN
}

@Serializable
data class MessageContent(val value: String? = null, val gain : Float? = null)

@Serializable
data class MgmMessage( val agentName: String,
                        val destination: String,
                        val messageType: MessageType,
                        val messageContent: MessageContent,
)