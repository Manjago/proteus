; =============================================================================
; Adam v4 - The First Self-Replicating Organism (OPTIMIZED)
; =============================================================================
; 
; This is the "ancestor" from which all life evolves.
; 12 instructions, position-independent code (ISA v1.2).
;
; OPTIMIZATION: Address comparison instead of counter.
; - Saves 2 instructions vs v3 (14 -> 12, 14% smaller)
; - No need for R0 counter or MOVI R0,0 instructions
;
; Strategy:
;   1. Get my address and calculate end address
;   2. Allocate space for child
;   3. Copy my genome to child (with possible mutations)
;   4. Spawn child as independent organism
;   5. Repeat forever
;
; Registers:
;   R3 - Child start address (from ALLOCATE, preserved for SPAWN)
;   R4 - Genome size (12)
;   R5 - Source pointer (incrementing)
;   R6 - Destination pointer (incrementing)
;   R7 - End address (my_addr + SIZE, used as loop bound)
;
; =============================================================================

start:
    GETADDR R7          ; R7 = my start address
    MOVI R4, 12         ; R4 = genome size (this organism is 12 instructions!)
    ALLOCATE R4, R3     ; Request memory, R3 = child address (or -1 if failed)

; Initialize copy pointers and calculate end address
    MOV R5, R7          ; R5 = source pointer (start of my genome)
    MOV R6, R3          ; R6 = destination pointer (start of child)
    ADD R7, R4          ; R7 = end address = my_addr + SIZE (overwrite R7!)

; Copy loop - this is where MUTATION can happen!
loop:
    COPY R5, R6         ; memory[R6] = memory[R5] (may mutate!)
    INC R5              ; src++
    INC R6              ; dst++
    JLT R5, R7, loop    ; if src < end, continue loop (-4 offset)

; Spawn child and restart
    SPAWN R3, R4        ; Register child as new organism
    JMP start           ; Go back and make another child (-12 offset)

; =============================================================================
; Notes:
; - COPY instruction is where evolution happens (mutations)
; - If ALLOCATE fails (returns -1), COPY writes to invalid address -> errors
; - If SPAWN fails, child memory remains allocated but unused
; - Organism runs forever until killed by reaper (age/errors)
; - R7 is rewritten each iteration: my_addr -> end_addr -> my_addr (by GETADDR)
; - R3 is preserved through the copy loop (not incremented!)
; =============================================================================
