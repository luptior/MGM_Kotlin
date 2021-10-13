import kotlinx.coroutines.*

suspend fun main() = coroutineScope {

    val dcopProblem = DCOPProblem()

    val agentsPorts = HashMap<String, Int>()
    for (agentName in dcopProblem.agentNames) {
        agentsPorts[agentName] = 5000 + dcopProblem.agentNames.indexOf(agentName)
    }

    // build agents
    val agentNameMap = HashMap<String, MgmAgent>()

    for (agentName in dcopProblem.agentNames) {
        val neighbors = mutableListOf<String>()
        val agentIndex = dcopProblem.agentNames.indexOf(agentName)
        for (i in 0 until dcopProblem.edgesBetweenAgents.size) {
            if (dcopProblem.edgesBetweenAgents[agentIndex][i] == 1) {
                neighbors.add(dcopProblem.agentNames[i])
            }
        }
        val newAgent = MgmAgent(
            agentName,
            dcopProblem.agentNames.indexOf(agentName),
            dcopProblem.agentDomains[agentName]!!,
            OptimizationMode.MAX,
            10,
            neighbors,
            5000 + dcopProblem.agentNames.indexOf(agentName),
            agentsPorts
        )
        agentNameMap[agentName] = newAgent
    }

    // start agents

    for (agentName in dcopProblem.agentNames) {
        launch(Dispatchers.Default) {
            agentNameMap[agentName]?.run()
        }
    }
}