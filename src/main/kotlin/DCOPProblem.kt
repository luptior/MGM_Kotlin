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
    val agents: List<String> = listOf("a1", "a2", "a3")
    val domains: HashMap<String, List<String>> = HashMap()
    val edges: Array<IntArray>
    val utility: HashMap<List<*>, Double> = HashMap()

    /**
     * TODO: Return cartesian product of domains
     *     private fun solutionSpace(): List<List<String>>? {
     *            var solutions: MutableList<List<String>>? = null
     *            return solutions
     *     }
     */

    fun toPrettyString() = "$agents\n$domains\n${edges.joinToString(postfix = " \n")}$utility"

    init {
        val domain = mutableListOf("R", "G")
        for (a in agents) {
            domains[a] = domain
        }
        edges = Array(agents.size) { IntArray(agents.size) }
        for (i in agents.indices) {
            for (j in agents.indices) {
                edges[i][j] = if (i != j) 1 else 0
                if (i == 0 && j == 2 || i == 2 && j == 0) {
                    edges[i][j] = 0
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
        utility[listOf("R", "R", "R")] = 20.1
        utility[listOf("R", "R", "G")] = 9.9
        utility[listOf("R", "G", "G")] = 9.7
        utility[listOf("G", "G", "G")] = 19.9
        utility[listOf("G", "G", "R")] = 10.1
        utility[listOf("G", "R", "R")] = 10.3
        utility[listOf("R", "G", "R")] = -0.1
        utility[listOf("G", "R", "G")] = 0.1
    }
}