import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class MgmMessageTest {
    @Test
    fun mgmMessageTest_Serialization(){
        val msg = MgmMessage(
            "a",
            "b",
            MessageType.GAIN,
            MessageContent(gain=0.01f)
        )

        val encoded = ProtoBuf.encodeToByteArray(msg)

        val decoded = ProtoBuf.decodeFromByteArray<MgmMessage>(encoded)

        assertEquals(msg, decoded)
    }
}