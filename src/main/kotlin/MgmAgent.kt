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

class MgmAgent(
    private val name: String,
    val iD: Int,
    private val domain: List<String>,
    private val optMode: String = "max",
    val cycle_limit: Int = 10,
    neighbors: List<String>,
    port: Int,
    neighborsPorts: HashMap<String, Int>
) : Runnable {
    var currentValue: String?
        private set
    private var newValue: String? = null
    var currentUtility: Float
        private set
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

    internal inner class MessageHandler(s: Socket, dis: DataInputStream, dos: DataOutputStream) :
        Runnable {
        // Very simple calss for taking in the value and store it in the received
        private val dis: DataInputStream
        private val dos: DataOutputStream
        private val socket: Socket

        @kotlin.jvm.Volatile
        var recvd: ByteArray? = null
            private set

        override fun run() {
            while (true) {
                try {
                    // receive 1
                    recvd = dis.readBytes()
                    // System.out.println("    Msg received by " + name +"  "+ recvd);
                    socket.close()
                    break
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            try {
                // closing resources
                dis.close()
                dos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // Constructor
        init {
            this.socket = s
            this.dis = dis
            this.dos = dos
        }
    }

    // ClientHandler class
    internal inner class ClientHandler() : Runnable {
        @OptIn(ExperimentalSerializationApi::class)
        override fun run() {
            val ss: ServerSocket
//            var received: String
            recv = mutableListOf()
            println("Agent $name starts running")
            try {
                // server is listening on port 5056
                ss = ServerSocket(PORT)
                println("Agent $name gets port $PORT")

                // running infinite loop for getting
                // client request
                while (cycleCount < cycle_limit) {
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

                        ch.recvd?.let{ bytesMsg ->
                            val decodedMsg = ProtoBuf.decodeFromByteArray<MgmMessage>(bytesMsg)

                            when (decodedMsg.messageType) {
                                MessageType.VALUE -> {
                                    neighborsNewValues[decodedMsg.agentName] = decodedMsg.messageContent.value!!
                                    if (cycleCount == 0 && currentValue != null) {
                                        neighborsValues[decodedMsg.agentName] = decodedMsg.messageContent.value
                                        var bestSoFar = evaluateExtensional(name, currentValue!!)
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
                                        handleGainMesssage()
                                    }
                                }
                            }
                        }


                    } catch (e: java.lang.Exception) {
                        try {
                            socket?.close()
                            e.printStackTrace()
                        } catch (se: NullPointerException) {
                            se.printStackTrace()
                        }
                    }
                }
                println("AGENT: $name value: $currentValue utility: $currentUtility")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun sendValueMessage() {
        // send the current value to all its neighbors
        for (n: String in neighbors) {
            val toSend = MgmMessage(name, n, MessageType.VALUE, MessageContent(value = currentValue))
            neighborsPorts[n]?.let { sendMsg(it, toSend) }
        }

        // TODO:
        // pubsub: can publish the message to the channel of this agent
    }

    private fun sendGainMessage() {
        // send the current gain to all agents in neighbor list
        for (n: String in neighbors) {
//            val toSend = "$name/$n/gain/$gain"
            val toSend = MgmMessage(name, n, MessageType.GAIN, MessageContent(gain = gain))
            neighborsPorts.get(n)?.let { sendMsg(it, toSend) }
        }
        //println("In cycle:"+ cycle_count + ", "+ name + " gain:" +this.gain+" sent to "+ neighbors);

        // pubsub: can publish the message to the channel of this agent
    }

    //    public void getGainMessage(){
    //        for (int i = 0; i < neighbors.size(); i++) {
    //            mgmMessage msg = fetchFromChannel(neighbors.get(i));
    //            neighborsGains.replace(neighbors.get(i), msg.getGain());
    //        }
    //    }
    fun handleValueMessage() {
        // when all values updates have been received
        // given the new context received in this.neighborsNewValues
        // gain and newValue has been set by findOptAssignment()
        if (neighborsNewValues.size == neighbors.size) {
            status = AgentStatus.GAIN
//            neighborsValues = neighborsNewValues.clone() as HashMap<String, String>
            neighborsValues = neighborsNewValues
            //println("Status change: Agent " + name + " starts handling gain");
            findOptValue()
            sendGainMessage()
        }
    }

    fun handleGainMesssage() {
        // when all gain updates have been received
        // given the new context received in this.neighborsNewValues
        // gain and newValue has been set by findOptAssignment()

        if (neighborsGains.size == neighbors.size) {
            val allGains: HashMap<String, Float> = neighborsGains
            allGains[name] = gain
            if (optMode == "max") {
                val maxNeighbour: String = java.util.Collections.max<Map.Entry<String, Float>>(
                    allGains.entries,
                    java.util.Map.Entry.comparingByValue<String, Float>()
                ).key
                if (maxNeighbour == name) {
                    // if the max gain is current agent
                    currentValue = newValue
                    currentUtility += gain
                    // sendValueMessage();
                }
            } else if (optMode == "min") {
                val minNeighbour: String = java.util.Collections.min<Map.Entry<String, Float>>(
                    allGains.entries,
                    java.util.Map.Entry.comparingByValue<String, Float>()
                ).key
                if (minNeighbour == name) {
                    currentValue = newValue
                    currentUtility += gain
                }
            } else {
                println("Optimization Mode is not supporteds")
            }
            println(
                "In cycle: $cycleCount agent: $name neighbors: $neighborsValues " + "current ultility: " +
                        "${currentUtility - gain} gain: $gain"
            )
            neighborsGains = HashMap<String, Float>()
            // change status
            status = AgentStatus.VALUE
            cycleCount += 1
            sendValueMessage()
        }
    }

    private fun evaluateRelations(value: String): Float {
        var tempValue = 0f
        for (n: String in neighbors) {
            (neighborsValues[n]?.let { tempValue += evaluateValue(name, value, n, it) })
        }
        return tempValue
    }

    private fun findOptValue() {
        /**
         * find the best value in dcop domain based on current context
         * returns the best assignment and store in this.gain
         */
        var bestSoFar = currentUtility
        for (value: String in domain) {
            var newUtility = evaluateExtensional(name, value)
            newUtility += evaluateRelations(value)
            println("agent: $name value:$value u:$newUtility")
            if (newUtility > bestSoFar && (optMode == "max") ||
                newUtility < bestSoFar && (optMode == "min")
            ) {
                newValue = value
                bestSoFar = newUtility
            } else {
                newValue = currentValue
            }
        }
        gain = bestSoFar - currentUtility
        println("Agent $name current utility: $currentUtility gain: $gain neighbors $neighborsValues")
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
            if ((( agent1 == "a1" && agent2 == "a2" ||
                        agent1 == "a2" && agent2 == "a1")
                            && val1 == val2 )
            ) {
                value += 10f
            }
            if ((( agent1 == "a3" && agent2 == "a2" ||
                        agent1 == "a2" && agent2 == "a3")
                            && val1 == val2)
            ) {
                value += 10f
            }
            return value
        }

        private fun evaluateExtensional(name: String, val1: String): Float {
            var value = 0f
            when (name){
                "a1" -> value += if (val1 == "R") -0.1f else 0.1f
                "a2", "a3" -> value += if (val1 == "R") 0.1f else -0.1f
            }
            return value
        }
    }

    init {
        val dcopDomain = domain
        currentValue = dcopDomain[0] //Assign the first element of the dcop domain to be the current assignment
        currentUtility = 0f
        this.neighbors = neighbors
        neighborsValues = HashMap()
        neighborsNewValues = HashMap()
        neighborsGains = HashMap()
        PORT = port
        this.neighborsPorts = neighborsPorts
    }
}