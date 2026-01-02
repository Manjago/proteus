package io.github.manjago.proteus.debug;

import io.github.manjago.proteus.core.*;
import io.github.manjago.proteus.sim.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Records simulation frames for debugging and analysis.
 * 
 * Usage:
 * <pre>
 * FrameRecorder recorder = new FrameRecorder(simulator, 100); // record 100 cycles
 * simulator.addFrameRecorder(recorder);
 * simulator.run();
 * List<Frame> frames = recorder.getFrames();
 * </pre>
 */
public class FrameRecorder {
    
    private static final Logger log = LoggerFactory.getLogger(FrameRecorder.class);
    
    private final List<Frame> frames = new ArrayList<>();
    private final int maxFrames;
    private final Map<Integer, String> organismNames = new HashMap<>();
    
    // Pending events for current frame
    private final List<Frame.FrameEvent> pendingEvents = new ArrayList<>();
    
    public FrameRecorder(int maxFrames) {
        this.maxFrames = maxFrames;
    }
    
    /**
     * Set custom name for an organism.
     */
    public void setOrganismName(int orgId, String name) {
        organismNames.put(orgId, name);
    }
    
    /**
     * Get organism name (custom or default).
     */
    public String getOrganismName(int orgId) {
        return organismNames.getOrDefault(orgId, null);
    }
    
    /**
     * Record an event that will be included in the next frame.
     */
    public void recordEvent(Frame.FrameEvent event) {
        pendingEvents.add(event);
    }
    
    /**
     * Record a frame from current simulator state.
     * Called by Simulator at end of each cycle.
     */
    public void recordFrame(Simulator sim) {
        if (frames.size() >= maxFrames) {
            return; // Already recorded enough
        }
        
        Frame.Builder builder = new Frame.Builder(sim.getTotalCycles());
        
        // Record memory regions (non-zero)
        recordMemory(sim, builder);
        
        // Record organisms
        recordOrganisms(sim, builder);
        
        // Add pending events
        for (Frame.FrameEvent event : pendingEvents) {
            builder.addEvent(event);
        }
        pendingEvents.clear();
        
        frames.add(builder.build());
        
        if (frames.size() % 10 == 0) {
            log.debug("Recorded {} frames", frames.size());
        }
    }
    
    /**
     * Check if recording is complete.
     */
    public boolean isComplete() {
        return frames.size() >= maxFrames;
    }
    
    /**
     * Get recorded frames.
     */
    public List<Frame> getFrames() {
        return Collections.unmodifiableList(frames);
    }
    
    /**
     * Get number of recorded frames.
     */
    public int getFrameCount() {
        return frames.size();
    }
    
    /**
     * Clear all recorded frames.
     */
    public void clear() {
        frames.clear();
        pendingEvents.clear();
    }
    
    // ========== Private helpers ==========
    
    private void recordMemory(Simulator sim, Frame.Builder builder) {
        int soupSize = sim.getMemoryManager().getTotalMemory();
        var soup = sim.getSoup();
        
        // Find contiguous non-zero regions
        int regionStart = -1;
        List<Integer> regionData = new ArrayList<>();
        
        for (int i = 0; i < soupSize; i++) {
            int value = soup.get(i);
            
            if (value != 0) {
                if (regionStart == -1) {
                    regionStart = i;
                }
                regionData.add(value);
            } else {
                if (regionStart != -1) {
                    // End of region
                    builder.addRegion(regionStart, toIntArray(regionData));
                    regionStart = -1;
                    regionData.clear();
                }
            }
        }
        
        // Don't forget last region
        if (regionStart != -1) {
            builder.addRegion(regionStart, toIntArray(regionData));
        }
    }
    
    private void recordOrganisms(Simulator sim, Frame.Builder builder) {
        for (Organism org : sim.getAliveOrganisms()) {
            CpuState state = org.getState();
            
            // Copy registers manually (no getRegisters() method)
            int[] regs = new int[8];
            if (state != null) {
                for (int i = 0; i < 8; i++) {
                    regs[i] = state.getRegister(i);
                }
            }
            
            Frame.OrganismSnapshot snapshot = new Frame.OrganismSnapshot(
                org.getId(),
                organismNames.get(org.getId()),
                org.getStartAddr(),
                org.getSize(),
                state != null ? state.getIp() : 0,
                regs,
                org.getErrors(),
                org.getBirthCycle(),
                org.getParentId(),
                state != null && state.hasPendingAllocation(),
                state != null ? state.getPendingAllocAddr() : 0,
                state != null ? state.getPendingAllocSize() : 0
            );
            
            builder.addOrganism(snapshot);
        }
    }
    
    private int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
