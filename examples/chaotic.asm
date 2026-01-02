; =============================================================================
; Chaotic v1 - Random Shooter Organism
; =============================================================================
;
; Strategy: Replicate first, attack rarely!
;
; This organism prioritizes survival over aggression:
;   - Makes 5 replications
;   - Then fires ONE shot into "random" memory via STORE
;   - Repeat
;
; The "randomness" comes from incrementing the attack offset each time.
; By replicating 5x before each attack, we reduce friendly fire risk.
;
; Uses STORE (relative addressing) to write outside our own genome.
; This is simpler than SEARCH+COPY, but less targeted.
;
; Size: 24 instructions
; =============================================================================

start:
    GETADDR R7          ; R7 = my start address
    MOVI R4, 24         ; R4 = my size (24 instructions)

; === Allocate and replicate ===
replicate:
    ALLOCATE R4, R3     ; R3 = child address
    
    ; Initialize copy pointers
    MOV R5, R7          ; R5 = source (me)
    MOV R6, R3          ; R6 = destination (child)
    MOVI R0, 0          ; R0 = copy counter

copy_loop:
    COPY R5, R6
    INC R5
    INC R6
    INC R0
    JMPN R0, R4, copy_loop

    ; Spawn child
    SPAWN R3, R4
    
    ; Increment replication counter
    INC R1              ; R1 = number of successful replications
    
    ; Check: have we replicated 5 times?
    MOVI R0, 5
    JMPN R1, R0, replicate   ; if R1 < 5, keep replicating

; === Attack phase ===
attack:
    ; Reset replication counter
    MOVI R1, 0
    
    ; Fire! Write garbage to a "random" location outside our genome
    ; R2 holds our attack offset (grows each attack cycle)
    ; We add 100 to spread shots around memory
    MOVI R0, 100
    ADD R2, R2, R0      ; R2 = R2 + 100
    
    ; The "weapon": write 0 (NOP) to memory at startAddr + R2
    ; Since R2 > our size (24), this writes OUTSIDE our genome!
    MOVI R0, 0          ; R0 = NOP instruction
    STORE R2, R0        ; soup[startAddr + R2] = 0
    
    ; Go back to replicating
    JMP replicate

; =============================================================================
; How it works:
;
; 1. Replicates 5 times (R1 counts 0→1→2→3→4→5)
; 2. When R1 reaches 5, enters attack phase
; 3. Attack: writes NOP to address (startAddr + R2)
;    - R2 starts at 0, grows by 100 each attack
;    - First attack: offset 100 (way outside our 24-byte genome)
;    - Second attack: offset 200, etc.
; 4. Resets R1 and continues replicating
;
; Advantages:
; - 5:1 replication:attack ratio reduces self-harm
; - Simple code (no SEARCH complexity)
; - Spreads damage across memory over time
;
; Disadvantages:
; - Untargeted: mostly hits empty memory or garbage
; - Predictable pattern (every 100 cells)
; - Doesn't specifically target other organisms
;
; =============================================================================
