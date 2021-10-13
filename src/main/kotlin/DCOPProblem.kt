/**
 * Class describing a sample DCOP problem.
 *
 * agents, a list of agents names in String
 * domains, <K></K>,V> for agent ids and List of domain
 * edges, 2D matrix, if adjacent 1, otherwise 0
 * utility, [val1, val2, val3] = 10.1
 *
 * TODO:
 * 1, Combinatorics
 * 2, Reads Scenario Files
 */
class DCOPProblem {
    val agentNames: List<String> = listOf("a1", "a2", "a3")
    val agentDomains: HashMap<String, List<String>> = HashMap()
    val edgesBetweenAgents: Array<IntArray>
    private val combinationUtility: HashMap<List<String>, Double> = HashMap()

    /**
     * TODO: Return cartesian product of domains
     *     private fun solutionSpace(): List<List<String>>? {
     *            var solutions: MutableList<List<String>>? = null
     *            return solutions
     *     }
     */

    fun toPrettyString() =
        "$agentNames\n$agentDomains\n${edgesBetweenAgents.joinToString(postfix = " \n")}$combinationUtility"

    init {
        val domain = mutableListOf("R", "G")
        for (agentName in agentNames) {
            agentDomains[agentName] = domain
        }
        edgesBetweenAgents = Array(agentNames.size) { IntArray(agentNames.size) }
        for (i in agentNames.indices) {
            for (j in agentNames.indices) {
                edgesBetweenAgents[i][j] = if (i != j) 1 else 0
                if (i == 0 && j == 2 || i == 2 && j == 0) {
                    edgesBetweenAgents[i][j] = 0
                }
            }
        }

        /**
         * A simple sample rule of utility(min the best)
         * if v1 = v2, utility += 10 otherwise 0
         * if v3 = v2, utility += 10 otherwise 0
         * if v1 = G, utility += 0.1 otherwise -= 0.1
         * if v2 = R, utility += 0.1 otherwise -= 0.1
         * if v3 = R, utility += 0.1 otherwise -= 0.1
         */
        combinationUtility[listOf("R", "R", "R")] = 20.1
        combinationUtility[listOf("R", "R", "G")] = 9.9
        combinationUtility[listOf("R", "G", "G")] = 9.7
        combinationUtility[listOf("G", "G", "G")] = 19.9
        combinationUtility[listOf("G", "G", "R")] = 10.1
        combinationUtility[listOf("G", "R", "R")] = 10.3
        combinationUtility[listOf("R", "G", "R")] = -0.1
        combinationUtility[listOf("G", "R", "G")] = 0.1
    }
}