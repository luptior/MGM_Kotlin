fun main() {
    val p = DCOPProblem()

    val agentsPorts = HashMap<String, Int>()
    for (a in p.agents) {
        agentsPorts[a] = 5000 + p.agents.indexOf(a)
    }

    // build agents
    val agentDict = HashMap<String, mgmAgent>()

    for (a in p.agents) {
        val neighbors = mutableListOf<String>()
        val index = p.agents.indexOf(a)
        for (i in 0 until p.edges.size) {
            if (p.edges[index][i] == 1) {
                neighbors.add(p.agents[i])
            }
        }
        val newAgent = mgmAgent(
            a,
            p.agents.indexOf(a),
            p.domains[a]!!,
            "max",
            10,
            neighbors,
            5000 + p.agents.indexOf(a),
            agentsPorts
        )
        agentDict[a] = newAgent
    }

    // start agents
    for (a in p.agents) {
        val t = Thread(agentDict[a])
        t.start()
    }
}