; =============================================================================
; Parasite v3 - Saboteur Organism (Optimized)
; =============================================================================
;
; Strategy: Find Adam organisms and sabotage their replication!
; 
; How it works:
;   1. First, replicate itself (like Adam v4 - address comparison)
;   2. Use SEARCH to find Adam's signature (MOVI R4, 12)
;   3. Overwrite found instruction with NOP → victim can't replicate correctly
;
; Key insight: The SEARCH template must be stored INSIDE this organism's genome.
; We store the exact instruction we're looking for, then skip over it with JMP.
;
; Size: 23 instructions (optimized with address comparison like Adam v4)
;
; Instruction layout:
;   0-11:  Self-replication (Adam v4 style)
;   12:    adam_signature (MOVI R4, 12) - DATA, not executed
;   13:    nop_weapon (NOP) - DATA, not executed  
;   14-22: Attack code
; =============================================================================

start:
    GETADDR R7          ; R7 = my start address
    MOVI R4, 23         ; R4 = my size (23 instructions)

; === Allocate space for child ===
    ALLOCATE R4, R3     ; R3 = child address (or -1 if failed)

; === Initialize copy pointers (Adam v4 style) ===
    MOV R5, R7          ; R5 = source (me)
    MOV R6, R3          ; R6 = destination (child)
    ADD R7, R4          ; R7 = end address (my_addr + SIZE)

; === Copy loop (no counter needed!) ===
copy_loop:
    COPY R5, R6         ; Copy with possible mutation
    INC R5
    INC R6
    JLT R5, R7, copy_loop   ; if src < end, continue

; === Spawn child and jump to attack ===
    SPAWN R3, R4
    JMP attack          ; Skip over data section

; =============================================================================
; DATA SECTION (not executed, used by SEARCH and COPY)
; Position 12
; =============================================================================
adam_signature:
    MOVI R4, 12         ; Adam v4 signature - our search template

; Position 13
nop_weapon:
    NOP                 ; What we'll overwrite with

; =============================================================================
; ATTACK CODE (position 14)
; =============================================================================
attack:
    ; Set up SEARCH parameters
    GETADDR R7          ; R7 = my start address (refresh after spawn loop)
    MOVI R1, 12         ; Rt = template offset (adam_signature is at position 12)
    ADD R1, R7          ; R1 = absolute address of template
    MOVI R0, 0          ; Rs = start search from address 0
    MOVI R2, 1          ; Rl = template length (1 instruction)
    SEARCH R0, R1, R2, R3   ; R3 = absolute address of found match, or -1

    ; If nothing found (R3 == -1), we'll write to invalid address -> error
    ; That's okay, errors just accumulate until we die

    ; Attack: copy NOP to victim's MOVI R4, 12 instruction
    ; nop_weapon is at offset 13 (adam_signature + 1)
    INC R1              ; R1 = address of nop_weapon (was 12+R7, now 13+R7)
    COPY R1, R3         ; Copy our NOP to victim's location!

    JMP start           ; Repeat forever

; =============================================================================
; Notes:
; - This parasite is 23 instructions (vs Adam's 12)
; - Uses Adam v4 optimization (address comparison, no counter)
; - The attack finds and overwrites MOVI R4, 12 with NOP
; - If victim uses "salted" MOVI (.word with different encoding), immune!
; - SEARCH may find our own template - we overwrite our own data with NOP,
;   but that's harmless since we jump over it anyway
; =============================================================================
