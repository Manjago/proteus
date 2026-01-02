package io.github.manjago.proteus.core;

import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.RandomProviderState;
import org.apache.commons.rng.simple.RandomSource;

import java.io.*;

/**
 * Deterministic random number generator with save/restore state capability.
 * 
 * Uses Apache Commons RNG XO_RO_SHI_RO_128_PP algorithm:
 * - Fast and high quality
 * - State is just 2 longs (128 bits)
 * - Supports explicit save/restore for checkpoints
 * 
 * IMPORTANT: Do not change RandomSource between versions!
 * Changing algorithm would break replay determinism.
 */
public final class GameRng {
    
    /**
     * Fixed algorithm - DO NOT CHANGE for backwards compatibility.
     * XoRoShiRo128++ is fast, high-quality, and has small state (128 bits).
     */
    private static final RandomSource ALGORITHM = RandomSource.XO_RO_SHI_RO_128_PP;
    
    private final long initialSeed;
    private final RestorableUniformRandomProvider rng;
    
    /**
     * Create new RNG with given seed.
     */
    public GameRng(long seed) {
        this.initialSeed = seed;
        this.rng = ALGORITHM.create(seed);
    }
    
    /**
     * Create RNG from saved state.
     */
    private GameRng(long initialSeed, RandomProviderState state) {
        this.initialSeed = initialSeed;
        this.rng = ALGORITHM.create(initialSeed);
        this.rng.restoreState(state);
    }
    
    // ========== Random Methods (compatible with java.util.Random API) ==========
    
    /**
     * Returns uniformly distributed int.
     */
    public int nextInt() {
        return rng.nextInt();
    }
    
    /**
     * Returns uniformly distributed int in [0, bound).
     */
    public int nextInt(int bound) {
        return rng.nextInt(bound);
    }
    
    /**
     * Returns uniformly distributed long.
     */
    public long nextLong() {
        return rng.nextLong();
    }
    
    /**
     * Returns uniformly distributed double in [0, 1).
     */
    public double nextDouble() {
        return rng.nextDouble();
    }
    
    /**
     * Returns true with probability p.
     */
    public boolean nextBoolean(double probability) {
        return rng.nextDouble() < probability;
    }
    
    /**
     * Returns uniformly distributed boolean.
     */
    public boolean nextBoolean() {
        return rng.nextBoolean();
    }
    
    /**
     * Fill byte array with random bytes.
     */
    public void nextBytes(byte[] bytes) {
        rng.nextBytes(bytes);
    }
    
    // ========== State Management ==========
    
    /**
     * Get initial seed (for logging/debugging).
     */
    public long getInitialSeed() {
        return initialSeed;
    }
    
    /**
     * Save current state for checkpoint.
     * Call this at the START of checkpoint creation, before any more random calls.
     */
    public GameRngState saveState() {
        return new GameRngState(initialSeed, rng.saveState());
    }
    
    /**
     * Restore RNG from saved state.
     */
    public static GameRng restore(GameRngState state) {
        return new GameRng(state.initialSeed(), state.state());
    }
    
    // ========== State Record ==========
    
    /**
     * Immutable snapshot of RNG state for serialization.
     */
    public record GameRngState(long initialSeed, RandomProviderState state) implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        /**
         * Serialize to bytes for storage.
         */
        public byte[] toBytes() {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeLong(initialSeed);
                oos.writeObject(state);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to serialize RNG state", e);
            }
        }
        
        /**
         * Deserialize from bytes.
         */
        public static GameRngState fromBytes(byte[] bytes) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                long seed = ois.readLong();
                RandomProviderState state = (RandomProviderState) ois.readObject();
                return new GameRngState(seed, state);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to deserialize RNG state", e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("RNG state class not found", e);
            }
        }
    }
}
