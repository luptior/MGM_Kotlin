import kotlin.concurrent.thread

fun main() {
    val p = DCOPProblem()

    val agentsPorts = HashMap<String, Int>()
    p.agents.forEachIndexed { index, agentName ->
        agentsPorts[agentName] = 5000 + index
    }

    // build agents
    val agentDict = HashMap<String, MgmAgent>()

    p.agents.forEachIndexed { index, agentName ->
        val neighbors = mutableListOf<String>()
        for (i in 0 until p.edges.size) {
            if (p.edges[index][i] == 1) {
                neighbors.add(p.agents[i])
            }
        }
        val newAgent = MgmAgent(
            agentName,
            p.agents.indexOf(agentName),
            p.domains[agentName]!!,
            OptimizationMode.MAX,
            10,
            neighbors,
            5000 + p.agents.indexOf(agentName),
            agentsPorts
        )
        agentDict[agentName] = newAgent
    }

    // start agents
    agentDict.values.forEach{  thread { it.run() } }

}