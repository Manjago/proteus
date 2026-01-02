; =============================================================================
; Parasite v2 - Saboteur Organism
; =============================================================================
;
; Strategy: Find Adam organisms and sabotage their replication!
; 
; How it works:
;   1. First, replicate itself (like Adam)
;   2. Use SEARCH to find Adam's signature (MOVI R4, 14)
;   3. Overwrite found instruction with NOP → victim can't replicate correctly
;
; Key insight: The SEARCH template must be stored INSIDE this organism's genome.
; We store the exact instruction we're looking for, then skip over it with JMP.
;
; Size: 22 instructions
; =============================================================================

start:
    GETADDR R7          ; R7 = my start address
    MOVI R4, 22         ; R4 = my size (22 instructions)

; === Allocate space for child ===
    ALLOCATE R4, R3     ; R3 = child address (or -1 if failed)

; === Initialize copy pointers ===
    MOV R5, R7          ; R5 = source (me)
    MOV R6, R3          ; R6 = destination (child)
    MOVI R0, 0          ; R0 = counter

; === Copy loop ===
copy_loop:
    COPY R5, R6         ; Copy with possible mutation
    INC R5
    INC R6
    INC R0
    JMPN R0, R4, copy_loop

; === Spawn child ===
    SPAWN R3, R4

; === Now attack! Skip over the data section ===
    JMP attack

; =============================================================================
; DATA SECTION (not executed, used by SEARCH)
; =============================================================================
adam_signature:
    MOVI R4, 14         ; This is what Adam looks like - our search template

nop_weapon:
    NOP                 ; What we'll overwrite with

; =============================================================================
; ATTACK CODE
; =============================================================================
attack:
    ; Set up SEARCH parameters
    ; Template is at offset 14 (adam_signature label)
    ; We search starting from offset 0
    MOVI R0, 0          ; Rs = start search from offset 0
    MOVI R1, 14         ; Rt = template offset (adam_signature is at position 14)
    MOVI R2, 1          ; Rl = template length (1 instruction)
    SEARCH R0, R1, R2, R3   ; R3 = absolute address of found match, or -1

    ; If R3 == -1, nothing found, restart
    ; (Simple check: if R3 is negative, skip attack)
    ; Note: We don't have a good "jump if negative" so we just try anyway

    ; Attack: copy NOP over the found location
    ; nop_weapon is at offset 15
    GETADDR R7          ; Refresh R7 (might have changed after SPAWN?)
    MOVI R1, 15         ; Offset of nop_weapon
    ADD R5, R7, R1      ; R5 = absolute address of our NOP
    COPY R5, R3         ; Copy our NOP to victim's location!

    ; Reset and repeat
    MOVI R0, 0
    JMP start

; =============================================================================
; Notes:
; - This parasite is 22 instructions (larger than Adam's 14)
; - The attack is simple: find and overwrite MOVI R4, 14
; - If victim uses "salted" MOVI (.word 0x0280FFFE), we won't find them!
; - SEARCH may find our own template - but that's in our own memory,
;   overwriting it with NOP doesn't hurt us (we jump over it anyway)
; =============================================================================
