import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.io.DataInputStream
import java.io.DataOutputStream

import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray

/**
 * current default to be seeking maximum
 */

enum class AgentStatus {
    GAIN,
    VALUE
}

enum class OptimizationMode {
    MAX,
    MIN
}

class MgmAgent(
    private val agentName: String,
    val uuid: Int,
    private val agentDomain: List<String>,
    private val optMode: OptimizationMode = OptimizationMode.MAX,
    val cycleLimit: Int = 10,
    agentNeighbors: List<String>,
    agentPort: Int,
    neighborsPorts: HashMap<String, Int>
) : Runnable {
    var currentValue: String?
        private set
    var currentUtility: Float
        private set
    private var newValue: String? = null
    private var gain = 0f
    private var status = AgentStatus.VALUE
    var cycleCount = 0
    var recv: List<String>? = null

    // neighbor related fields
    private val neighbors: List<String>
    private val neighborsPorts: HashMap<String, Int>
    private var neighborsValues: HashMap<String, String>
    private val neighborsNewValues: HashMap<String, String>
    private var neighborsGains: HashMap<String, Float>

    //TODO: read and store the utility map from dcop problem
    // private val agentUtilityMap: HashMap<String, HashMap<String, HashMap<String, Int>>>? = null

    // communication related fields
    private val ip: InetAddress? = null
    private val PORT: Int
    override fun run() {
        // the main run process for each agent
        val ch = ClientHandler()

        // where agent spawn the thread to handle each income request
        val chThread = Thread(ch)
        chThread.start()

        // wait a second for the thread to start before messages are sent
        try {
            TimeUnit.SECONDS.sleep(1)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        // trigger the whole process with first message
        if (cycleCount == 0) {
            sendValueMessage()
        }
    }

    internal inner class MessageHandler(
        private val socket: Socket,
        private val dis: DataInputStream,
        private val dos: DataOutputStream
    ) : Runnable {
        // Very simple class for taking in the value and store it in the received

        @kotlin.jvm.Volatile
        var recvd: ByteArray? = null
            private set

        override fun run() {

            while (true) {
                try {
                    recvd = dis.readBytes()
                    socket.close()
                    break
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            try {
                dis.close()
                dos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // ClientHandler class
    internal inner class ClientHandler() : Runnable {

        @OptIn(ExperimentalSerializationApi::class)
        override fun run() {
            val ss: ServerSocket
            recv = mutableListOf()
            println("Agent $agentName starts running")
            try {
                // server is listening on port 5056
                ss = ServerSocket(PORT)
                println("Agent $agentName gets port $PORT")

                // running infinite loop for getting client request
                while (cycleCount < cycleLimit) {
                    var socket: Socket? = null
                    try {
                        // socket object to receive incoming client requests\
                        socket = ss.accept()

                        // obtaining input and out streams
                        val dis = DataInputStream(socket.getInputStream())
                        val dos = DataOutputStream(socket.getOutputStream())

                        // create a new thread object
                        val ch = MessageHandler(socket, dis, dos)
                        val t = Thread(ch)

                        // Invoking the start() method
                        t.start()
                        t.join()

                        ch.recvd?.let { bytesMsg ->
                            val decodedMsg = ProtoBuf.decodeFromByteArray<MgmMessage>(bytesMsg)

                            when (decodedMsg.messageType) {
                                MessageType.VALUE -> {
                                    neighborsNewValues[decodedMsg.agentName] = decodedMsg.messageContent.value!!
                                    if (cycleCount == 0 && currentValue != null) {
                                        neighborsValues[decodedMsg.agentName] = decodedMsg.messageContent.value
                                        var bestSoFar = evaluateExtensional(agentName, currentValue!!)
                                        currentValue?.let { bestSoFar += evaluateRelations(it) }
                                        currentUtility = bestSoFar
                                    }
                                    if (status == AgentStatus.VALUE) {
                                        handleValueMessage()
                                    }
                                }
                                MessageType.GAIN -> {
                                    neighborsGains[decodedMsg.agentName] = decodedMsg.messageContent.gain!!
                                    if (status == AgentStatus.GAIN) {
                                        handleGainMessage()
                                    }
                                }
                            }
                        }


                    } catch (e: java.lang.Exception) {
                        try {
                            socket?.close()
                            e.printStackTrace()
                        } catch (socketException: NullPointerException) {
                            socketException.printStackTrace()
                        }
                    }
                }
                println("AGENT: $agentName value: $currentValue utility: $currentUtility")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * send the current value to all its neighbors
     */
    private fun sendValueMessage() {
        for (n: String in neighbors) {
            val toSend = MgmMessage(agentName, n, MessageType.VALUE, MessageContent(value = currentValue))
            neighborsPorts[n]?.let { sendMsg(it, toSend) }
        }

        // TODO:
        // pubsub: can publish the message to the channel of this agent
    }

    /**
     * send the current gain to all agents in neighbor list
     */
    private fun sendGainMessage() {
        for (n: String in neighbors) {
            val toSend = MgmMessage(agentName, n, MessageType.GAIN, MessageContent(gain = gain))
            neighborsPorts[n]?.let { sendMsg(it, toSend) }
        }

        // TODO: can publish the message to the channel of this agent
    }

    /**
     * when all values updates have been received
     * given the new context received in this.neighborsNewValues
     * gain and newValue has been set by findOptAssignment()
     */
    fun handleValueMessage() {
        if (neighborsNewValues.size == neighbors.size) {
            status = AgentStatus.GAIN
            neighborsValues = neighborsNewValues
            findOptValue()
            sendGainMessage()
        }
    }

    /**
     * when all gain updates have been received
     * given the new context received in this.neighborsNewValues
     * gain and newValue has been set by findOptAssignment()
     */
    fun handleGainMessage() {
        if (neighborsGains.size == neighbors.size) {
            val allGains: HashMap<String, Float> = neighborsGains
            allGains[agentName] = gain
            if (optMode == OptimizationMode.MAX) {
                val maxNeighbour: String = java.util.Collections.max<Map.Entry<String, Float>>(
                    allGains.entries,
                    java.util.Map.Entry.comparingByValue()
                ).key
                if (maxNeighbour == agentName) { // if the max gain is current agent
                    currentValue = newValue
                    currentUtility += gain
                    // sendValueMessage();
                }
            } else if (optMode == OptimizationMode.MIN) {
                val minNeighbour: String = java.util.Collections.min<Map.Entry<String, Float>>(
                    allGains.entries,
                    java.util.Map.Entry.comparingByValue()
                ).key
                if (minNeighbour == agentName) {
                    currentValue = newValue
                    currentUtility += gain
                }
            } else {
                println("Optimization Mode is not supported")
            }
            println(
                "In cycle: $cycleCount agent: $agentName neighbors: $neighborsValues " + "current utility: " +
                        "${currentUtility - gain} gain: $gain"
            )
            neighborsGains = HashMap()
            // change status
            status = AgentStatus.VALUE
            cycleCount += 1
            sendValueMessage()
        }
    }

    private fun evaluateRelations(value: String): Float {
        var tempValue = 0f
        for (n: String in neighbors) {
            (neighborsValues[n]?.let { tempValue += evaluateValue(agentName, value, n, it) })
        }
        return tempValue
    }

    private fun findOptValue() {
        /**
         * find the best value in dcop domain based on current context
         * returns the best assignment and store in this.gain
         */
        var bestSoFar = currentUtility
        for (value: String in agentDomain) {
            var newUtility = evaluateExtensional(agentName, value)
            newUtility += evaluateRelations(value)
            println("agent: $agentName value:$value u:$newUtility")
            if (newUtility > bestSoFar && (optMode == OptimizationMode.MAX) ||
                newUtility < bestSoFar && (optMode == OptimizationMode.MIN)
            ) {
                newValue = value
                bestSoFar = newUtility
            } else {
                newValue = currentValue
            }
        }
        gain = bestSoFar - currentUtility
        println("Agent $agentName current utility: $currentUtility gain: $gain neighbors $neighborsValues")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun sendMsg(port: Int, msg: MgmMessage) {
        try {
            // getting localhost ip
            val ip: InetAddress = InetAddress.getByName("localhost")

            // establish the connection with server port 5056
            val s = Socket(ip, port)

            // obtaining input and out streams
            val dis = DataInputStream(s.getInputStream())
            val dos = DataOutputStream(s.getOutputStream())

            //send 1, the msg gonna be read
            val bytes = ProtoBuf.encodeToByteArray(msg)
            dos.write(bytes)
            s.close()
            dis.close()
            dos.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private fun evaluateValue(agent1: String, val1: String, agent2: String, val2: String): Float {
            /**
             * compare the utility given that this agent chooses val1 and agent2 choose val2, currently hardcoded
             * TODO: return agentUtilityMap[val1][agent2][val2]
             */
            var value = 0f
            if (((agent1 == "a1" && agent2 == "a2" ||
                        agent1 == "a2" && agent2 == "a1")
                        && val1 == val2)
            ) {
                value += 10f
            }
            if (((agent1 == "a3" && agent2 == "a2" ||
                        agent1 == "a2" && agent2 == "a3")
                        && val1 == val2)
            ) {
                value += 10f
            }
            return value
        }

        private fun evaluateExtensional(name: String, val1: String): Float {
            var value = 0f
            when (name) {
                "a1" -> value += if (val1 == "R") -0.1f else 0.1f
                "a2", "a3" -> value += if (val1 == "R") 0.1f else -0.1f
            }
            return value
        }
    }

    init {
        val dcopDomain = agentDomain
        currentValue = dcopDomain[0] //Assign the first element of the dcop domain to be the current assignment
        currentUtility = 0f
        this.neighbors = agentNeighbors
        neighborsValues = HashMap()
        neighborsNewValues = HashMap()
        neighborsGains = HashMap()
        PORT = agentPort
        this.neighborsPorts = neighborsPorts
    }
}