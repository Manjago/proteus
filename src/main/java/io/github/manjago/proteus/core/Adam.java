package io.github.manjago.proteus.core;

/**
 * Adam - the first self-replicating organism.
 * <p>
 * This is the "ancestor" from which all other organisms will evolve.
 * Adam's genome contains the minimal code needed to:
 * 1. Calculate its own size
 * 2. Allocate memory for offspring
 * 3. Copy itself to the new location
 * 4. Spawn the offspring
 * 5. Repeat forever
 * <p>
 * Register conventions:
 * - R0: source pointer (for copy loop)
 * - R1: destination pointer (for copy loop)
 * - R2: copy counter (cells remaining)
 * - R3: child address (from ALLOCATE)
 * - R4: genome size
 * - R5: copy loop address
 * - R6: start address (0 for Adam)
 * - R7: temp
 */
public final class Adam {
    
    private Adam() {}
    
    /**
     * Build Adam's genome.
     * <p>
     * Adam is designed to be loaded at address 0.
     * The genome builds its own size constant using arithmetic.
     */
    public static int[] genome() {
        GenomeBuilder b = GenomeBuilder.create();
        
        // === PHASE 1: Build constants ===
        // We need to build the SIZE constant in R4.
        // Using doubling: INC, ADD, ADD, ADD... is more efficient than N*INC
        
        // R6 = 0 (start address) - registers start at 0, so already done
        // R4 = size (will be computed below)
        // R5 = copy loop address (will be computed below)
        
        // First, let's lay out the structure and count instructions:
        //
        // [0-4]   Build size constant (5 instructions)
        // [5-7]   Build loop address constant (3 instructions)
        // [8]     ALLOCATE
        // [9-11]  Setup copy loop registers (3 instructions)
        // [12-16] Copy loop (5 instructions) <- R5 points here
        // [17]    SPAWN
        // [18]    JMP to start
        //
        // Total: 19 instructions
        
        // --- Build SIZE = 19 in R4 ---
        // Strategy: 1 -> 2 -> 4 -> 8 -> 16 -> 19 (add 3)
        b.inc(4);           // [0] R4 = 1
        b.add(4, 4);        // [1] R4 = 2
        b.add(4, 4);        // [2] R4 = 4
        b.add(4, 4);        // [3] R4 = 8
        b.add(4, 4);        // [4] R4 = 16
        b.inc(4);           // [5] R4 = 17
        b.inc(4);           // [6] R4 = 18
        b.inc(4);           // [7] R4 = 19
        
        // --- Build COPY_LOOP address = 14 in R5 ---
        // Strategy: 1 -> 2 -> 4 -> 8 -> 16 -> 14 (sub 2)
        b.inc(5);           // [8] R5 = 1
        b.add(5, 5);        // [9] R5 = 2
        b.add(5, 5);        // [10] R5 = 4
        b.add(5, 5);        // [11] R5 = 8
        b.inc(5);           // [12] R5 = 9... wait, let me recalculate
        
        // Actually, let me recalculate positions:
        // [0-7]: 8 instructions for building R4=19
        // [8-?]: building R5
        // then ALLOCATE, setup, copy loop, SPAWN, JMP
        
        // Let me restart with exact counting...
        
        return buildGenomeV2();
    }
    
    /**
     * Build Adam's genome v2 - carefully counted.
     */
    private static int[] buildGenomeV2() {
        GenomeBuilder b = GenomeBuilder.create();
        
        // We'll build the genome, then adjust constants based on actual size.
        // This is iterative - we build, count, adjust, rebuild.
        
        // Final structure after iteration:
        //
        // ADDR  INSTRUCTION          COMMENT
        // ----  -----------          -------
        // 0     INC R4               R4 = 1
        // 1     ADD R4, R4           R4 = 2
        // 2     ADD R4, R4           R4 = 4
        // 3     ADD R4, R4           R4 = 8
        // 4     ADD R4, R4           R4 = 16
        // 5     INC R4               R4 = 17
        // 6     INC R4               R4 = 18
        // 7     INC R4               R4 = 19
        // 8     INC R4               R4 = 20
        // 9     INC R5               R5 = 1
        // 10    ADD R5, R5           R5 = 2
        // 11    ADD R5, R5           R5 = 4
        // 12    ADD R5, R5           R5 = 8
        // 13    ADD R5, R5           R5 = 16
        // 14    ALLOCATE R4, R3      Allocate R4 bytes, result in R3
        // 15    MOV R1, R3           R1 = child address (dest)
        // 16    MOV R2, R4           R2 = size (counter)
        // 17    COPY R0, R1          copy_loop: memory[R1] = memory[R0]
        // 18    INC R0               R0++
        // 19    INC R1               R1++
        // 20    DEC R2               R2--
        // 21    JMPN R2, R5          if R2 != 0, jump to copy_loop (addr 16)
        // 22    SPAWN R3, R4         Create child organism
        // 23    JMP R6               Jump to start (R6 = 0)
        //
        // Total: 24 instructions
        // SIZE constant: 24
        // COPY_LOOP address: 17
        
        // --- Build SIZE = 24 in R4 ---
        // 1 -> 2 -> 4 -> 8 -> 16 -> 24 (need +8)
        // 16 + 8 = 24, and we have R4=16, need to add 8
        // But we used R4 for doubling. Let's use R7 as temp.
        // Or: 1->2->4->8->16, then INC 8 times = 8 more instructions
        // Or: 1->2->4->8->16->32, then SUB to get 24
        //     32 - 8 = 24. We need 8 in another register.
        //     R7: 1->2->4->8 (4 instructions)
        //     Then SUB R4, R7
        
        // Let's do: build 32 in R4, build 8 in R7, SUB
        b.inc(4);           // [0] R4 = 1
        b.add(4, 4);        // [1] R4 = 2
        b.add(4, 4);        // [2] R4 = 4
        b.add(4, 4);        // [3] R4 = 8
        b.add(4, 4);        // [4] R4 = 16
        b.add(4, 4);        // [5] R4 = 32
        b.inc(7);           // [6] R7 = 1
        b.add(7, 7);        // [7] R7 = 2
        b.add(7, 7);        // [8] R7 = 4
        b.add(7, 7);        // [9] R7 = 8
        b.sub(4, 7);        // [10] R4 = 32 - 8 = 24
        
        // --- Build COPY_LOOP = 17 in R5 ---
        // 1 -> 2 -> 4 -> 8 -> 16 -> 17
        b.inc(5);           // [11] R5 = 1
        b.add(5, 5);        // [12] R5 = 2
        b.add(5, 5);        // [13] R5 = 4
        b.add(5, 5);        // [14] R5 = 8
        b.add(5, 5);        // [15] R5 = 16
        b.inc(5);           // [16] R5 = 17
        
        // --- ALLOCATE ---
        b.allocate(4, 3);   // [17] Allocate R4 bytes, child addr in R3
        
        // --- Setup copy loop ---
        // R0 = 0 (already, it's the source = start of genome)
        // R1 = child address
        // R2 = counter
        b.mov(1, 3);        // [18] R1 = R3 (child addr)
        b.mov(2, 4);        // [19] R2 = R4 (size)
        
        // --- Copy loop ---         // copy_loop is at address 20
        b.copy(0, 1);       // [20] memory[R1] = memory[R0]
        b.inc(0);           // [21] R0++
        b.inc(1);           // [22] R1++
        b.dec(2);           // [23] R2--
        b.jmpn(2, 5);       // [24] if R2 != 0, jump to R5 (copy_loop)
        
        // --- SPAWN ---
        b.spawn(3, 4);      // [25] Spawn child at R3 with size R4
        
        // --- Loop forever ---
        b.jmp(6);           // [26] Jump to R6 (start = 0)
        
        // Total: 27 instructions
        // Oops, size is 27, not 24! Need to recalculate...
        
        // Let me just hardcode this properly with iteration.
        return buildGenomeFinal();
    }
    
    /**
     * Build Adam's genome - final version with correct constants.
     * 
     * I'll build it step by step and verify the size.
     */
    private static int[] buildGenomeFinal() {
        // First pass: build without worrying about constants
        GenomeBuilder b = GenomeBuilder.create();
        
        // We need to build SIZE and COPY_LOOP_ADDR
        // Let's mark where copy_loop will be and what size we get
        
        // PHASE 1: Build SIZE in R4 (we'll adjust after)
        b.inc(4);           // R4 = 1
        b.add(4, 4);        // R4 = 2
        b.add(4, 4);        // R4 = 4
        b.add(4, 4);        // R4 = 8
        b.add(4, 4);        // R4 = 16
        b.add(4, 4);        // R4 = 32
        // Now adjust: we'll know final size after building everything
        
        // PHASE 2: Build COPY_LOOP_ADDR in R5
        b.inc(5);           // R5 = 1
        b.add(5, 5);        // R5 = 2
        b.add(5, 5);        // R5 = 4
        b.add(5, 5);        // R5 = 8
        b.add(5, 5);        // R5 = 16
        // Adjust after
        
        // PHASE 3: Adjust constants
        // We know where we are now, let's continue and see
        int preAllocatePos = b.size(); // Position before ALLOCATE
        
        // ALLOCATE
        b.allocate(4, 3);   // Allocate R4 bytes, child addr in R3
        
        // Setup copy
        b.mov(1, 3);        // R1 = child addr
        b.mov(2, 4);        // R2 = size
        
        int copyLoopPos = b.size(); // Position of copy loop
        
        // Copy loop
        b.copy(0, 1);       // memory[R1] = memory[R0]
        b.inc(0);           // R0++
        b.inc(1);           // R1++
        b.dec(2);           // R2--
        b.jmpn(2, 5);       // if R2 != 0, jump to R5
        
        // SPAWN
        b.spawn(3, 4);      // Spawn child
        
        // Loop forever
        b.jmp(6);           // Jump to start (R6 = 0)
        
        int totalSize = b.size();
        
        // Now I know:
        // - totalSize (the SIZE constant we need in R4)
        // - copyLoopPos (the address for R5)
        
        // Let's rebuild with correct adjustments
        return buildGenomeWithConstants(totalSize, copyLoopPos);
    }
    
    private static int[] buildGenomeWithConstants(int targetSize, int copyLoopAddr) {
        // After phase 1 (build 32 in R4): R4 = 32, size = 6 instructions
        // After phase 2 (build 16 in R5): R5 = 16, size = 11 instructions
        // We need adjustments.
        
        // Let me just build a clean version with exact math.
        // 
        // The issue is that the adjustment instructions change the size,
        // which changes what we need to adjust to. It's a fixed-point problem.
        //
        // For simplicity, let me use more INC instructions to hit exact targets.
        
        GenomeBuilder b = GenomeBuilder.create();
        
        // Careful construction:
        //
        // We want SIZE=26, COPY_LOOP_ADDR=18 (estimated)
        //
        // Build R4 = 26:
        //   1->2->4->8->16->32, then we need 32-6=26, so SUB 6
        //   To get 6: 1->2->4, then INC twice: 6
        //   That's 4+2=6 more instructions... adds to size!
        //
        // This is getting circular. Let me take a different approach:
        // Just use INC many times. It's inefficient but correct.
        
        // === Approach: use R7 as a helper to build constants ===
        
        // Actually, the cleanest way: pre-calculate everything, 
        // accept a fixed genome size, and hard-code the constant loading.
        
        // GENOME SIZE: 24 instructions (I'll verify)
        //
        // [0-5]:   Build R4 = 24 (using 32-8)
        // [6-11]:  Build R5 = 16 (copy loop addr)
        // [12]:    Adjust R5 += 4 to get 20
        // [13]:    ALLOCATE R4, R3
        // [14]:    MOV R1, R3
        // [15]:    MOV R2, R4
        // [16-20]: Copy loop (5 instructions) at addr 16
        // [21]:    SPAWN R3, R4
        // [22]:    JMP R6
        //
        // Hmm that's 23. Let me recount...
        
        // Okay, let's just build it instruction by instruction and be done:
        
        b.inc(4);           // 0: R4 = 1
        b.add(4, 4);        // 1: R4 = 2
        b.add(4, 4);        // 2: R4 = 4
        b.add(4, 4);        // 3: R4 = 8
        b.add(4, 4);        // 4: R4 = 16
        b.add(4, 4);        // 5: R4 = 32
        
        b.inc(7);           // 6: R7 = 1
        b.add(7, 7);        // 7: R7 = 2
        b.add(7, 7);        // 8: R7 = 4
        b.add(7, 7);        // 9: R7 = 8
        
        b.sub(4, 7);        // 10: R4 = 32 - 8 = 24  <- SIZE
        
        b.add(5, 7);        // 11: R5 = 0 + 8 = 8
        b.add(5, 7);        // 12: R5 = 8 + 8 = 16   <- COPY_LOOP will be at 16
        
        b.allocate(4, 3);   // 13: Allocate, child addr in R3
        
        b.mov(1, 3);        // 14: R1 = child addr (dest ptr)
        b.mov(2, 4);        // 15: R2 = size (counter)
        
        // copy_loop: (address 16)
        b.copy(0, 1);       // 16: memory[R1] = memory[R0]
        b.inc(0);           // 17: R0++
        b.inc(1);           // 18: R1++
        b.dec(2);           // 19: R2--
        b.jmpn(2, 5);       // 20: if R2 != 0, jump to R5 (16)
        
        b.spawn(3, 4);      // 21: Spawn child
        
        b.jmp(6);           // 22: Jump to start (R6 = 0)
        
        // WAIT - that's 23 instructions (0-22), not 24!
        // R4 = 24 but genome is 23. Off by one.
        
        // Add a NOP at the end to make it 24:
        b.nop();            // 23: NOP (padding)
        
        // Now: 24 instructions [0-23], SIZE=24 ✓, COPY_LOOP=16 ✓
        
        return b.build();
    }
    
    /**
     * Get Adam's genome size.
     */
    public static int size() {
        return genome().length;
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
        System.out.println("R4: genome size (24)");
        System.out.println("R5: copy loop address (16)");
        System.out.println("R6: start address (0)");
        System.out.println("R7: temp for building constants");
    }
}
