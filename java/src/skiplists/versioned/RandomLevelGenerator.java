package skiplists.versioned;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class RandomLevelGenerator {
    /**
     * Generates the initial random seed for the cheaper per-instance random number generators used in randomLevel.
     */
    private static final Random seedGenerator = new Random();

    /**
     * Seed for simple random number generator. Not volatile since it doesn't matter too much if different threads don't
     * see updates.
     */
    private static transient int randomSeed = seedGenerator.nextInt() | 0x0100;

    /**
     * Returns a random level for inserting a new node. Hardwired to k=1, p=0.5, max 31 (see above and Pugh's
     * "Skip List Cookbook", sec 3.4).
     * 
     * This uses the simplest of the generators described in George Marsaglia's "Xorshift RNGs" paper. This is not a
     * high-quality generator but is acceptable here.
     */
    public static int randomLevel() {
        int x = randomSeed;
        x ^= x << 13;
        x ^= x >>> 17;
        randomSeed = x ^= x << 5;
        if ((x & 0x80000001) != 0) // test highest and lowest bits
            return 1;
        int level = 1;
        while (((x >>>= 1) & 1) != 0)
            ++level;
        return level;
    }

    public static void main(String[] args) {
        Map<Integer, Integer> stat = new HashMap<Integer, Integer>();

        int v;

        for (int i = 0; i < 10000; i++) {
            v = randomLevel();
            if (stat.containsKey(v)) {
                stat.put(v, stat.get(v) + 1);
            } else {
                stat.put(v, 1);
            }
        }

        for (Entry<Integer, Integer> entry : stat.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }
}
