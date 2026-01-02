package io.github.manjago.proteus.debug;

import java.util.List;
import java.util.ArrayList;

/**
 * Single frame of simulation recording.
 * 
 * Contains complete state at one cycle:
 * - Soup snapshot (only non-zero regions)
 * - All organisms with their state
 * - Events that happened this cycle
 */
public record Frame(
    long cycle,
    List<MemoryRegion> memoryRegions,
    List<OrganismSnapshot> organisms,
    List<FrameEvent> events
) {
    
    /**
     * A contiguous region of non-zero memory.
     */
    public record MemoryRegion(int startAddr, int[] data) {
        public int endAddr() {
            return startAddr + data.length;
        }
    }
    
    /**
     * Snapshot of one organism's state.
     */
    public record OrganismSnapshot(
        int id,
        String name,           // Custom name (or null)
        int startAddr,
        int size,
        int ip,                // Relative instruction pointer
        int[] registers,       // R0-R7
        int errorCount,
        long birthCycle,
        int parentId,
        
        // Pending allocation (if any)
        boolean hasPending,
        int pendingAddr,
        int pendingSize
    ) {}
    
    /**
     * Event that occurred during this cycle.
     */
    public sealed interface FrameEvent {
        
        record Spawn(int childId, int parentId, int addr, int size) implements FrameEvent {}
        
        record Death(int orgId, String reason) implements FrameEvent {}
        
        record Mutation(int orgId, int addr, int originalValue, int mutatedValue) implements FrameEvent {}
        
        record Instruction(int orgId, int addr, int instruction, String disasm) implements FrameEvent {}
        
        record Allocation(int orgId, int addr, int size) implements FrameEvent {}
        
        record AllocationFailed(int orgId, int requestedSize) implements FrameEvent {}
        
        record Error(int orgId, String errorType, String details) implements FrameEvent {}
    }
    
    /**
     * Builder for creating frames.
     */
    public static class Builder {
        private final long cycle;
        private final List<MemoryRegion> regions = new ArrayList<>();
        private final List<OrganismSnapshot> organisms = new ArrayList<>();
        private final List<FrameEvent> events = new ArrayList<>();
        
        public Builder(long cycle) {
            this.cycle = cycle;
        }
        
        public Builder addRegion(int startAddr, int[] data) {
            regions.add(new MemoryRegion(startAddr, data.clone()));
            return this;
        }
        
        public Builder addOrganism(OrganismSnapshot org) {
            organisms.add(org);
            return this;
        }
        
        public Builder addEvent(FrameEvent event) {
            events.add(event);
            return this;
        }
        
        public Frame build() {
            return new Frame(cycle, List.copyOf(regions), List.copyOf(organisms), List.copyOf(events));
        }
    }
}
