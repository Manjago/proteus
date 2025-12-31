package io.github.manjago.proteus.sim;

import io.github.manjago.proteus.core.Organism;

/**
 * Listener for simulation events.
 * 
 * Implement this interface to react to simulation events,
 * for example to log events to a file or update a UI.
 */
public interface SimulatorListener {
    
    /**
     * Called when a new organism is spawned.
     * 
     * @param child the new organism
     * @param parent the parent organism (null for Adam)
     * @param cycle current simulation cycle
     */
    default void onSpawn(Organism child, Organism parent, long cycle) {}
    
    /**
     * Called when an organism dies.
     * 
     * @param organism the dead organism
     * @param cause reason for death
     * @param cycle current simulation cycle
     */
    default void onDeath(Organism organism, DeathCause cause, long cycle) {}
    
    /**
     * Called when a mutation occurs during COPY.
     * 
     * @param destAddr address where mutation landed
     * @param originalValue original instruction
     * @param mutatedValue mutated instruction
     * @param cycle current simulation cycle
     */
    default void onMutation(int destAddr, int originalValue, int mutatedValue, long cycle) {}
    
    /**
     * Called periodically with progress statistics.
     * 
     * @param stats current statistics
     */
    default void onProgress(SimulatorStats stats) {}
    
    /**
     * Called when a checkpoint should be saved.
     * 
     * @param cycle current cycle
     */
    default void onCheckpoint(long cycle) {}
    
    /**
     * No-op listener that does nothing.
     */
    SimulatorListener NOOP = new SimulatorListener() {};
}
