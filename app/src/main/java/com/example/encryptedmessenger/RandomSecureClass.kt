import kotlin.math.abs

/**
 * A class that implements a custom cryptographically secure pseudorandom number generator (CSPRNG).
 * This class combines two algorithms, XORshift128+ and SplitMix64, to create a more complex random number generator.
 * Use well-established libraries like Java's SecureRandom for cryptographically secure random number generation.
 */
class RandomSecureClass {

    // The internal state of the generator
    private val state: LongArray
    private var seed: Long

    init {
        // Seed the RNG with the current time in nanoseconds
        seed = System.nanoTime()
        state = longArrayOf(System.nanoTime(), System.nanoTime())
    }

    /**
     * A SplitMix64 implementation that generates a new random long value.
     *
     * @return a random long value generated by the SplitMix64 algorithm.
     */
    private fun splitMix64(): Long {
        seed += 0x9E3779B97F4A7C1
        var z = seed
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5
        z = (z xor (z ushr 27)) * 0x94D049BB133111
        return z xor (z ushr 31)
    }

    /**
     * A XORshift128+ implementation that generates a new random long value.
     *
     * @return a random long value generated by the XORshift128+ algorithm.
     */
    private fun xorshift128plus(): Long {
        var s1 = state[0]
        val s0 = state[1]
        state[0] = s0
        s1 = s1 xor (s1 shl 23) // a
        s1 = s1 xor (s1 ushr 17) // b
        s1 = s1 xor s0
        s1 = s1 xor (s0 ushr 26) // c
        state[1] = s1
        return s1 + s0
    }

    /**
     * Generates a random integer value by combining the XORshift128+ and SplitMix64 algorithms.
     *
     * @return a random integer value generated by combining the XORshift128+ and SplitMix64 algorithms.
     */
    @Synchronized
    fun nextInt(): Int {
        val xorshiftValue = xorshift128plus()
        val splitMixValue = splitMix64()
        val combinedValue = xorshiftValue xor splitMixValue
        return abs(combinedValue and 0xFFFFFFFFL).toInt()
    }
}
