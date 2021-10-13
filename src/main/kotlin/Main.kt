import kotlinx.coroutines.*

suspend fun main() = coroutineScope {

    val dcopProblem = DCOPProblem()

    val agentsPorts = HashMap<String, Int>()
    for (agentName in dcopProblem.agents) {
        agentsPorts[agentName] = 5000 + dcopProblem.agents.indexOf(agentName)
    }

    // build agents
    val agentNameMap = HashMap<String, MgmAgent>()

    for (agentName in dcopProblem.agents) {
        val neighbors = mutableListOf<String>()
        val agentIndex = dcopProblem.agents.indexOf(agentName)
        for (i in 0 until dcopProblem.edges.size) {
            if (dcopProblem.edges[agentIndex][i] == 1) {
                neighbors.add(dcopProblem.agents[i])
            }
        }
        val newAgent = MgmAgent(
            agentName,
            dcopProblem.agents.indexOf(agentName),
            dcopProblem.domains[agentName]!!,
            OptimizationMode.MAX,
            10,
            neighbors,
            5000 + dcopProblem.agents.indexOf(agentName),
            agentsPorts
        )
        agentNameMap[agentName] = newAgent
    }

    // start agents

    for (agentName in dcopProblem.agents) {
        launch(Dispatchers.Default) {
            agentNameMap[agentName]?.run()
        }
    }
}