package io.github.manjago.proteus.core;

/**
 * Adam v3 - the first self-replicating organism (ISA v1.2).
 * 
 * This is the "ancestor" from which all other organisms will evolve.
 * 
 * <h2>v3 Changes (Position-Independent Code):</h2>
 * <ul>
 *   <li>Uses GETADDR to get absolute start address</li>
 *   <li>Uses relative JMP offsets instead of absolute addresses</li>
 *   <li>Works correctly from any memory location</li>
 *   <li>Can be safely defragmented (moved in memory)</li>
 * </ul>
 * 
 * <h2>Register Usage:</h2>
 * <ul>
 *   <li>R0: counter (0 to size-1)</li>
 *   <li>R3: child absolute address (from ALLOCATE)</li>
 *   <li>R4: genome size (SIZE = 14)</li>
 *   <li>R5: source pointer (absolute, incremented)</li>
 *   <li>R6: destination pointer (absolute, incremented)</li>
 *   <li>R7: my absolute start address (from GETADDR)</li>
 * </ul>
 */
public final class Adam {
    
    /** Adam's genome size in instructions */
    public static final int SIZE = 14;
    
    private Adam() {}
    
    /**
     * Build Adam's genome (v3, Position-Independent).
     * 
     * <pre>
     * 0:  MOVI R4, 14      ; R4 = genome size
     * 1:  GETADDR R7       ; R7 = my absolute start address
     * 2:  ALLOCATE R4, R3  ; R3 = child's absolute address
     * 
     * ; Prepare pointers
     * 3:  MOV R5, R7       ; R5 = src pointer (my start)
     * 4:  MOV R6, R3       ; R6 = dst pointer (child start)
     * 5:  MOVI R0, 0       ; R0 = counter
     * 
     * ; copy_loop (starts at IP=6):
     * 6:  COPY R5, R6      ; memory[R6] = memory[R5] (may mutate!)
     * 7:  INC R5           ; src++
     * 8:  INC R6           ; dst++
     * 9:  INC R0           ; counter++
     * 10: JLT R0, R4, -5  ; if (R0 < R4) goto 6
     * 
     * 11: SPAWN R3, R4     ; Create child organism
     * 12: MOVI R0, 0       ; Reset counter
     * 13: JMP -12          ; goto 2 (ALLOCATE)
     * </pre>
     * 
     * Jump calculation: JMP -12 at IP=13 → advanceIp → IP=14 → jumpRelative(-12) → IP=2
     */
    public static int[] genome() {
        return GenomeBuilder.create()
            // Constants and setup
            .movi(4, SIZE)          // 0: R4 = SIZE (14)
            .getaddr(7)             // 1: R7 = my start address
            .allocate(4, 3)         // 2: R3 = child address
            
            // Prepare pointers
            .mov(5, 7)              // 3: R5 = R7 (src pointer)
            .mov(6, 3)              // 4: R6 = R3 (dst pointer)
            .movi(0, 0)             // 5: R0 = 0 (counter)
            
            // copy_loop (address 6):
            .copy(5, 6)             // 6: memory[R6] = memory[R5]
            .inc(5)                 // 7: src++
            .inc(6)                 // 8: dst++
            .inc(0)                 // 9: counter++
            .jlt(0, 4, -5)          // 10: if R0 < R4, jump to 6
            
            // Spawn and repeat
            .spawn(3, 4)            // 11: Spawn child
            .movi(0, 0)             // 12: Reset counter
            .jmp(-12)               // 13: Jump to 2 (ALLOCATE)
            .build();
    }
    
    /**
     * Get Adam's genome size.
     */
    public static int size() {
        return SIZE;
    }
    
    /**
     * Get disassembly of Adam's genome.
     */
    public static String disassemble() {
        return Disassembler.disassembleWithHex(genome(), 0);
    }
    
    /**
     * Print Adam's genome for debugging.
     */
    public static void main(String[] args) {
        System.out.println("=== ADAM v3: Position-Independent Self-Replicator ===");
        System.out.println("Size: " + size() + " instructions\n");
        System.out.println(disassemble());
        System.out.println("\n=== Register Usage ===");
        System.out.println("R0: counter (0 to size-1)");
        System.out.println("R3: child address (from ALLOCATE)");
        System.out.println("R4: genome size (" + SIZE + ")");
        System.out.println("R5: source pointer (absolute, incremented)");
        System.out.println("R6: destination pointer (absolute, incremented)");
        System.out.println("R7: my start address (from GETADDR)");
    }
}
