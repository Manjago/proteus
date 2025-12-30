package io.github.manjago.proteus.core;

/**
 * Adam - the first self-replicating organism.
 * 
 * This is the "ancestor" from which all other organisms will evolve.
 * Adam's genome contains the minimal code needed to:
 * 1. Load constants (SIZE, COPY_LOOP address)
 * 2. Allocate memory for offspring
 * 3. Copy itself to the new location
 * 4. Spawn the offspring
 * 5. Reset and repeat forever
 * 
 * With MOVI instruction, Adam is only 13 instructions!
 * 
 * Register conventions:
 * - R0: source pointer (starts at 0)
 * - R1: destination pointer
 * - R2: copy counter
 * - R3: child address (from ALLOCATE)
 * - R4: genome size (SIZE)
 * - R5: copy loop address (COPY_LOOP)
 * - R6: start address (always 0, never modified)
 * - R7: unused
 */
public final class Adam {
    
    /** Adam's genome size in instructions */
    public static final int SIZE = 13;
    
    /** Address of the copy loop within Adam's genome */
    public static final int COPY_LOOP = 5;
    
    private Adam() {}
    
    /**
     * Build Adam's genome.
     * 
     * Layout:
     * [0]:    MOVI R4, 13      ; R4 = SIZE
     * [1]:    MOVI R5, 5       ; R5 = COPY_LOOP address
     * [2]:    ALLOCATE R4, R3  ; Allocate SIZE cells, address in R3
     * [3]:    MOV R1, R3       ; R1 = destination pointer
     * [4]:    MOV R2, R4       ; R2 = copy counter
     * [5]:    COPY R0, R1      ; copy_loop: memory[R1] = memory[R0]
     * [6]:    INC R0           ; R0++
     * [7]:    INC R1           ; R1++
     * [8]:    DEC R2           ; R2--
     * [9]:    JMPN R2, R5      ; if R2 != 0, goto copy_loop
     * [10]:   SPAWN R3, R4     ; Create child organism
     * [11]:   SUB R0, R0       ; Reset R0 = 0 for next cycle
     * [12]:   JMP R6           ; Jump to start (R6 = 0)
     */
    public static int[] genome() {
        return GenomeBuilder.create()
            .movi(4, SIZE)          // 0: R4 = SIZE (13)
            .movi(5, COPY_LOOP)     // 1: R5 = COPY_LOOP (5)
            .allocate(4, 3)         // 2: Allocate R4 cells, addr in R3
            .mov(1, 3)              // 3: R1 = child address
            .mov(2, 4)              // 4: R2 = counter
            // copy_loop (address 5):
            .copy(0, 1)             // 5: memory[R1] = memory[R0]
            .inc(0)                 // 6: R0++
            .inc(1)                 // 7: R1++
            .dec(2)                 // 8: R2--
            .jmpn(2, 5)             // 9: if R2 != 0, goto copy_loop
            .spawn(3, 4)            // 10: Spawn child
            .sub(0, 0)              // 11: Reset R0 = 0
            .jmp(6)                 // 12: Jump to start (R6 = 0)
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
        System.out.println("=== ADAM: The First Self-Replicator ===");
        System.out.println("Size: " + size() + " instructions\n");
        System.out.println(disassemble());
        System.out.println("\n=== Register Usage ===");
        System.out.println("R0: source pointer (starts at 0)");
        System.out.println("R1: destination pointer");
        System.out.println("R2: copy counter");
        System.out.println("R3: child address (from ALLOCATE)");
        System.out.println("R4: genome size (" + SIZE + ")");
        System.out.println("R5: copy loop address (" + COPY_LOOP + ")");
        System.out.println("R6: start address (0)");
        System.out.println("R7: unused");
    }
}
