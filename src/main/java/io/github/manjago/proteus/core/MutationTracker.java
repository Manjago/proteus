package io.github.manjago.proteus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tracks all mutations that occur during simulation.
 * 
 * This allows post-hoc analysis of which mutations survived
 * (ended up in living organisms) vs which were lethal or lost.
 */
public class MutationTracker {
    
    private final List<MutationRecord> mutations = new ArrayList<>();
    private long currentCycle = 0;
    
    /**
     * Set the current simulation cycle (for timestamping mutations).
     */
    public void setCycle(long cycle) {
        this.currentCycle = cycle;
    }
    
    /**
     * Record a mutation event.
     * 
     * @param sourceAddr address being copied from
     * @param destAddr address being copied to (where mutation landed)
     * @param originalValue original instruction
     * @param mutatedValue mutated instruction
     */
    public void record(int sourceAddr, int destAddr, int originalValue, int mutatedValue) {
        mutations.add(new MutationRecord(currentCycle, sourceAddr, destAddr, originalValue, mutatedValue));
    }
    
    /**
     * Get all recorded mutations.
     */
    public List<MutationRecord> getAllMutations() {
        return new ArrayList<>(mutations);
    }
    
    /**
     * Get mutations that affected a specific memory range.
     */
    public List<MutationRecord> getMutationsInRange(int start, int size) {
        return mutations.stream()
                .filter(m -> m.affectsRange(start, size))
                .collect(Collectors.toList());
    }
    
    /**
     * Get mutations that ended up in living organisms.
     * These are the "survived" mutations.
     * 
     * @param organisms list of all organisms
     * @return mutations whose destAddr falls within a living organism's genome
     */
    public List<MutationRecord> getSurvivedMutations(List<Organism> organisms) {
        List<MutationRecord> survived = new ArrayList<>();
        
        for (MutationRecord mutation : mutations) {
            for (Organism org : organisms) {
                if (org.isAlive() && mutation.affectsRange(org.getStartAddr(), org.getSize())) {
                    survived.add(mutation);
                    break; // Don't double-count
                }
            }
        }
        
        return survived;
    }
    
    /**
     * Get unique survived mutations (deduplicated by destAddr).
     * If the same address was mutated multiple times, only the last mutation matters.
     */
    public List<MutationRecord> getUniqueSurvivedMutations(List<Organism> organisms) {
        List<MutationRecord> survived = getSurvivedMutations(organisms);
        
        // Keep only the latest mutation per destAddr
        return survived.stream()
                .collect(Collectors.toMap(
                        MutationRecord::destAddr,
                        m -> m,
                        (m1, m2) -> m1.cycle() > m2.cycle() ? m1 : m2
                ))
                .values()
                .stream()
                .sorted((a, b) -> Integer.compare(a.destAddr(), b.destAddr()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get statistics about mutations.
     */
    public String getStats() {
        if (mutations.isEmpty()) {
            return "No mutations recorded";
        }
        
        return String.format("Mutations: %d total", mutations.size());
    }
    
    /**
     * Clear all recorded mutations.
     */
    public void clear() {
        mutations.clear();
    }
    
    /**
     * Get total mutation count.
     */
    public int size() {
        return mutations.size();
    }
}
