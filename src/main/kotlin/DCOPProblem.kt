/**
 * Class describing a sample DCOP problem.
 *
 *
 * agents, a list of agents names in String
 * domains, <K></K>,V> for agent ids and List of domain
 * edges, 2D matrix, if adjacent 1, otherwise 0
 * utility, [val1, val2, val3] = 10.1
 *
 *
 * TODO:
 * 1, Combinatorics
 * 2, Reads Sceanario Files
 */
class DCOPProblem {
    //    private static final Logger logger = LoggerFactory.getLogger(DCOPProblem.class);
    var agents: List<String> = listOf("a1", "a2", "a3")
    var domains: HashMap<String, List<String>> = HashMap()
    var edges: Array<IntArray>
    var utility: HashMap<List<*>, Double> = HashMap()

    private fun solutionSpace(): List<List<String>>? {
        // Return cartesian product of domains

        // TODO: do the actual work here
        // might need some library which can do the combinatorial in Java

        var solutions: ArrayList<List<String>>? = null

        return solutions
    }

    /**
     * Utility Part
     */
    override fun toString(): String {
        var problemString = ""
        problemString += agents.toString() + "\n"
        problemString += domains.toString() + "\n"
        for (edge in edges) {
            problemString += edge.toString() + "\n"
        }
        problemString += utility.toString()
        return problemString
    }

    companion object {
        fun readSample(sampleFile: String?) {
            // TODO: actually parse sample from a file
        }
    }

    init {
        // default to have a sample question
        val domain = ArrayList<String>()
        domain.add("R")
        domain.add("G")
        for (a in agents) {
            domains.put(a, domain)
        }
        edges = Array(agents.size) { IntArray(agents.size) }
        for (i in agents.indices) {
            for (j in agents.indices) {
                if (i != j) {
                    edges[i][j] = 1
                } else {
                    edges[i][j] = 0
                }
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

//        logger.info("A dcop problem formed");
//        logger.info(toString());
    }
}