import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * current default to be seeking maximum
 */

class mgmAgent(
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
    private var status // should be 3 status starting/gain/value_update
            = "value"
    var cycleCount = 0
    var recv: List<String>? = null

    // neighbor related fields
    private val neighbors: ArrayList<String>
    private val neighborsPorts: HashMap<String, Int>
    private var neighborsValues: HashMap<String, String>
    private val neighborsNewValues: HashMap<String, String>
    private var neighborsGains: HashMap<String, Float>
    private val agentUtilityMap: HashMap<String, HashMap<String, HashMap<String, Int>>>? = null

    // communication related fields
    private val ip: java.net.InetAddress? = null
    private val PORT: Int
    override fun run() {
        // the main run process for each agent
        val ch = ClientHandler()

        // where agent spawn the thread to handle each income request
        val ch_thread: java.lang.Thread = java.lang.Thread(ch)
        ch_thread.start()

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

    internal inner class MessageHandler(s: Socket, dis: java.io.DataInputStream, dos: java.io.DataOutputStream) :
        Runnable {
        // Very simple calss for taking in the value and store it in the received
        private val dis: java.io.DataInputStream
        private val dos: java.io.DataOutputStream
        private val socket: Socket

        @kotlin.jvm.Volatile
        var recvd: String? = null
            private set

        override fun run() {
            while (true) {
                try {
                    // receive 1
                    recvd = dis.readUTF()
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
        override fun run() {
            val ss: ServerSocket
//            var received: String
            recv = java.util.ArrayList<String>()
            println("Agent $name starts running")
            try {
                // server is listening on port 5056
                ss = ServerSocket(PORT)
                println("Agent $name gets port $PORT")

                // running infinite loop for getting
                // client request
                while (cycleCount < cycle_limit) {
                    var socket: Socket? = null
                    var pMsg: HashMap<String, String>?
                    try {
                        // socket object to receive incoming client requests\
                        socket = ss.accept()


                        // obtaining input and out streams
                        val dis: java.io.DataInputStream = java.io.DataInputStream(socket.getInputStream())
                        val dos: java.io.DataOutputStream = java.io.DataOutputStream(socket.getOutputStream())

                        // create a new thread object
                        val ch: MessageHandler = MessageHandler(socket, dis, dos)
                        val t: java.lang.Thread = java.lang.Thread(ch)

                        // Invoking the start() method
                        t.start()
                        t.join()
                        pMsg = parseMsg(ch.recvd)

                        // System.out.println(status + pMsg);

                        when (pMsg["type"]) {
                            "value" -> {
                                neighborsNewValues[pMsg["origin"]!!] = pMsg["value"]!!
                                if (cycleCount == 0) {
                                    neighborsValues[pMsg["origin"]!!] = pMsg["value"]!!
                                    var bestSoFar = evaluateExtensional(name, currentValue)
                                    currentValue?.let { bestSoFar += evaluateRelations(it) }
                                    currentUtility = bestSoFar
                                }
                                if (status == "value") {
                                    handleValueMessage()
                                }
                            }
                            "gain" -> {
                                neighborsGains[pMsg["origin"]!!] = pMsg["value"]!!.toFloat()
                                if (status == "gain") {
                                    handleGainMesssage()
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

    fun getDcopDomain(): List<String> {
        return domain
    }

    fun getNeighbors(): java.util.ArrayList<String> {
        return neighbors
    }

    private fun sendValueMessage() {
        // send the current value to all its neighbors
        for (n: String in neighbors) {
            val toSend = "$name/$n/value/$currentValue"
            neighborsPorts[n]?.let { sendMsg(it, toSend) }
        }
        // System.out.println("In cycle:"+ cycle_count + ", "+ name + " value " + currentValue+ " sent to "+ neighbors);


        // TODO:
        // pubsub: can publish the message to the channel of this agent
    }

    private fun sendGainMessage() {
        // send the current gain to all agents in neighbor list
        for (n: String in neighbors) {
            val toSend = "$name/$n/gain/$gain"
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
            status = "gain"
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

        // System.out.println("Agent: " + name + " " + neighborsGains);
        if (neighborsGains.size == neighbors.size) {
//            val allGains: HashMap<String, Float> = neighborsGains.clone() as HashMap<String, Float>
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
                "In cycle: " + cycleCount + " agent: " + name + " neighbors: " + neighborsValues
                        + " current ultility: " + (currentUtility - gain) + " gain: " + gain
            )
            neighborsGains = HashMap<String, Float>()
            // change status
            status = "value"
            //System.out.println("Status change: Agent " + name + " starts handling value");
            cycleCount += 1
            sendValueMessage()
        }
    }

    private fun evaluateRelations(value: String): Float {
        var tempValue: Float = 0f
        for (n: String in neighbors) {
            (neighborsValues[n]?.let { tempValue += evaluateValue(name, value, n, it) })
        }
        return tempValue
    }

    private fun findOptValue() {
        // find the best value in dcop domain based on current context
        // returns the best assignment and store in this.gain
        var bestSoFar = currentUtility
        for (value: String in getDcopDomain()) {
            var newUtility = evaluateExtensional(name, value)
            newUtility += evaluateRelations(value)
            println("agent: $name value:$value u:$newUtility")
            //System.out.println("New Utility is" + newUtility + "current" + this.currentUtility);
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
        println("Agent $name current ultility: $currentUtility gain: $gain neighbors $neighborsValues")
    }

    fun sendMsg(port: Int, msg: String?) {
        try {
            // getting localhost ip
            val ip: java.net.InetAddress = java.net.InetAddress.getByName("localhost")

            // establish the connection with server port 5056
            val s = Socket(ip, port)

            // obtaining input and out streams
            val dis: java.io.DataInputStream = java.io.DataInputStream(s.getInputStream())
            val dos: java.io.DataOutputStream = java.io.DataOutputStream(s.getOutputStream())

            //send 1, the msg gonna be read
            dos.writeUTF(msg)

            //System.out.println("Msg \"" + msg +"\" sent from " + PORT + " to " + port);
            s.close()
            dis.close()
            dos.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private fun evaluateValue(agent1: String, val1: String, agent2: String, val2: String): Float {
            // compare the utility given that this agent chooses val1 and agent2 choose val2

            // return agentUtilityMap.get(val1).get(agent2).get(val2);

            // currently hardcoded
            var value = 0
            if ((((agent1 == "a1") && (agent2 == "a2") || (agent1 == "a2") && (agent2 == "a1"))
                        && (val1 == val2))
            ) {
                value += 10
            }
            if ((((agent1 == "a3") && (agent2 == "a2") || (agent1 == "a2") && (agent2 == "a3"))
                        && (val1 == val2))
            ) {
                value += 10
            }
            return value.toFloat()
        }

        private fun evaluateExtensional(name: String, val1: String?): Float {
            var `val` = 0f
            if ((name == "a1")) {
                if ((val1 == "R")) {
                    `val` -= 0.1f
                } else {
                    `val` += 0.1f
                }
            } else if ((name == "a2")) {
                if ((val1 == "R")) {
                    `val` += 0.1f
                } else {
                    `val` -= 0.1f
                }
            } else if ((name == "a3")) {
                if ((val1 == "R")) {
                    `val` += 0.1f
                } else {
                    `val` -= 0.1f
                }
            }
            return `val`
        }

        private fun parseMsg(msg: String?): HashMap<String, String> {
            // Msg : "orginAgentID-type-value"
            val reformattedMsg: HashMap<String, String> = HashMap<String, String>()
            val msgParts: Array<String> = msg!!.split("/").toTypedArray()
            reformattedMsg["origin"] = msgParts[0]
            reformattedMsg["dest"] = msgParts[1]
            reformattedMsg["type"] = msgParts[2]
            reformattedMsg["value"] = msgParts[3]
            return reformattedMsg
        }
    }

    init {
        // gain/value
        // can be max or min

        //Assign the first element of the dcop domain to be the current assignment
        var dcopDomain = domain
        currentValue = dcopDomain[0]
        currentUtility = 0f
        this.neighbors = neighbors as ArrayList<String>
        neighborsValues = HashMap()
        neighborsNewValues = HashMap()
        neighborsGains = HashMap()
        PORT = port
        this.neighborsPorts = neighborsPorts
    }
}