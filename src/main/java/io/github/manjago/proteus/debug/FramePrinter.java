package io.github.manjago.proteus.debug;

import io.github.manjago.proteus.core.Disassembler;
import io.github.manjago.proteus.core.OpCode;

import java.io.PrintStream;
import java.util.List;

/**
 * Prints recorded frames in human-readable format.
 */
public class FramePrinter {
    
    private final PrintStream out;
    private boolean showRegisters = true;
    private boolean showEvents = true;
    private boolean showMemory = true;
    private boolean compactMode = false;
    
    public FramePrinter() {
        this(System.out);
    }
    
    public FramePrinter(PrintStream out) {
        this.out = out;
    }
    
    public FramePrinter showRegisters(boolean show) {
        this.showRegisters = show;
        return this;
    }
    
    public FramePrinter showEvents(boolean show) {
        this.showEvents = show;
        return this;
    }
    
    public FramePrinter showMemory(boolean show) {
        this.showMemory = show;
        return this;
    }
    
    public FramePrinter compactMode(boolean compact) {
        this.compactMode = compact;
        return this;
    }
    
    /**
     * Print all frames.
     */
    public void printAll(List<Frame> frames) {
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) {
                out.println();
            }
            printFrame(frames.get(i), i);
        }
    }
    
    /**
     * Print single frame.
     */
    public void printFrame(Frame frame, int frameIndex) {
        out.println("═".repeat(70));
        out.printf("FRAME %d | Cycle %d%n", frameIndex, frame.cycle());
        out.println("═".repeat(70));
        
        // Events
        if (showEvents && !frame.events().isEmpty()) {
            out.println();
            out.println("📋 Events:");
            for (Frame.FrameEvent event : frame.events()) {
                printEvent(event);
            }
        }
        
        // Organisms
        out.println();
        out.println("👥 Organisms (" + frame.organisms().size() + "):");
        for (Frame.OrganismSnapshot org : frame.organisms()) {
            printOrganism(org);
        }
        
        // Memory
        if (showMemory && !frame.memoryRegions().isEmpty()) {
            out.println();
            out.println("💾 Memory (non-zero regions):");
            for (Frame.MemoryRegion region : frame.memoryRegions()) {
                printMemoryRegion(region, frame.organisms());
            }
        }
    }
    
    /**
     * Print frame summary (one line per frame).
     */
    public void printSummary(List<Frame> frames) {
        out.println("Frame | Cycle | Orgs | Events | Memory cells");
        out.println("------+-------+------+--------+--------------");
        
        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            int cells = f.memoryRegions().stream().mapToInt(r -> r.data().length).sum();
            out.printf("%5d | %5d | %4d | %6d | %d%n",
                i, f.cycle(), f.organisms().size(), f.events().size(), cells);
        }
    }
    
    // ========== Private helpers ==========
    
    private void printEvent(Frame.FrameEvent event) {
        String icon = switch (event) {
            case Frame.FrameEvent.Spawn s -> "🐣";
            case Frame.FrameEvent.Death d -> "💀";
            case Frame.FrameEvent.Mutation m -> "🧬";
            case Frame.FrameEvent.Instruction i -> "▶️";
            case Frame.FrameEvent.Allocation a -> "📦";
            case Frame.FrameEvent.AllocationFailed f -> "❌";
            case Frame.FrameEvent.Error e -> "⚠️";
        };
        
        String desc = switch (event) {
            case Frame.FrameEvent.Spawn s -> 
                String.format("Spawn: org #%d from parent #%d at addr %d (size %d)", 
                    s.childId(), s.parentId(), s.addr(), s.size());
            case Frame.FrameEvent.Death d -> 
                String.format("Death: org #%d (%s)", d.orgId(), d.reason());
            case Frame.FrameEvent.Mutation m -> 
                String.format("Mutation: org #%d at addr %d: 0x%08X → 0x%08X", 
                    m.orgId(), m.addr(), m.originalValue(), m.mutatedValue());
            case Frame.FrameEvent.Instruction i -> 
                String.format("Exec: org #%d at addr %d: %s", 
                    i.orgId(), i.addr(), i.disasm());
            case Frame.FrameEvent.Allocation a -> 
                String.format("Alloc: org #%d got %d cells at addr %d", 
                    a.orgId(), a.size(), a.addr());
            case Frame.FrameEvent.AllocationFailed f -> 
                String.format("Alloc failed: org #%d wanted %d cells", 
                    f.orgId(), f.requestedSize());
            case Frame.FrameEvent.Error e -> 
                String.format("Error: org #%d %s: %s", 
                    e.orgId(), e.errorType(), e.details());
        };
        
        out.printf("  %s %s%n", icon, desc);
    }
    
    private void printOrganism(Frame.OrganismSnapshot org) {
        String name = org.name() != null ? " \"" + org.name() + "\"" : "";
        out.printf("  #%d%s @ %d-%d (size %d), IP=%d, errors=%d, parent=#%d%n",
            org.id(), name, org.startAddr(), org.startAddr() + org.size() - 1,
            org.size(), org.ip(), org.errorCount(), org.parentId());
        
        if (showRegisters && !compactMode) {
            out.printf("     R0-R7: [%d, %d, %d, %d, %d, %d, %d, %d]%n",
                org.registers()[0], org.registers()[1], org.registers()[2], org.registers()[3],
                org.registers()[4], org.registers()[5], org.registers()[6], org.registers()[7]);
        }
        
        if (org.hasPending()) {
            out.printf("     📦 Pending allocation: addr=%d, size=%d%n",
                org.pendingAddr(), org.pendingSize());
        }
    }
    
    private void printMemoryRegion(Frame.MemoryRegion region, List<Frame.OrganismSnapshot> organisms) {
        out.printf("  [%d..%d] (%d cells)%n", 
            region.startAddr(), region.endAddr() - 1, region.data().length);
        
        if (compactMode) {
            return;
        }
        
        int[] data = region.data();
        for (int i = 0; i < data.length; i++) {
            int addr = region.startAddr() + i;
            int value = data[i];
            
            String disasm = Disassembler.disassemble(value);
            String owner = findOwner(addr, organisms);
            String ipMarker = isCurrentIP(addr, organisms) ? " <<<" : "";
            
            out.printf("    %5d: 0x%08X  %-25s %s%s%n", 
                addr, value, disasm, owner, ipMarker);
        }
    }
    
    private String findOwner(int addr, List<Frame.OrganismSnapshot> organisms) {
        for (Frame.OrganismSnapshot org : organisms) {
            if (addr >= org.startAddr() && addr < org.startAddr() + org.size()) {
                String name = org.name() != null ? org.name() : "org#" + org.id();
                int offset = addr - org.startAddr();
                return String.format("[%s +%d]", name, offset);
            }
        }
        return "[free]";
    }
    
    private boolean isCurrentIP(int addr, List<Frame.OrganismSnapshot> organisms) {
        for (Frame.OrganismSnapshot org : organisms) {
            int absoluteIP = org.startAddr() + org.ip();
            if (addr == absoluteIP) {
                return true;
            }
        }
        return false;
    }
}
