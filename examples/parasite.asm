; =============================================================================
; Parasite v1 - Saboteur Organism
; =============================================================================
;
; Strategy: Find and overwrite enemy organisms!
; Uses SEARCH to find patterns, then COPY garbage over them.
;
; THIS IS AN EXPERIMENTAL ORGANISM - may not work as intended!
; The goal is to demonstrate SEARCH and memory-writing capabilities.
;
; =============================================================================

start:
    GETADDR R7          ; R7 = my address
    MOVI R4, 20         ; R4 = my size (this organism)
    
; First, replicate myself (like Adam)
    ALLOCATE R4, R3     ; R3 = child address
    MOV R5, R7          ; R5 = src (me)
    MOV R6, R3          ; R6 = dst (child)
    MOVI R0, 0          ; R0 = counter

copy_loop:
    COPY R5, R6
    INC R5
    INC R6
    INC R0
    JMPN R0, R4, copy_loop
    SPAWN R3, R4

; Now try to find and attack enemies
; Search for "MOVI R4, 14" pattern (Adam's size instruction)
; This is encoded as: 0x02 (MOVI) + R4 (100) + immediate 14
attack:
    MOVI R0, 0          ; R0 = search start (offset from my position)
    MOVI R1, 1          ; R1 = template offset (instruction #1 = MOVI R4,14)
    MOVI R2, 1          ; R2 = template length (1 instruction)
    SEARCH R0, R1, R2, R3   ; R3 = found address (or -1)
    
; If found something, overwrite it with NOP
; (This will break the victim's replication!)
    MOVI R5, 0          ; R5 = NOP instruction
    COPY R5, R3         ; Overwrite found location with NOP
    
; Repeat forever
    JMP start

; =============================================================================
; Notes:
; - This organism is 20 instructions (larger than Adam's 14)
; - It replicates first, then tries to attack
; - SEARCH looks for patterns relative to this organism's memory
; - The attack may hit itself or its children - not very smart!
; - A smarter parasite would verify the target is not itself
; =============================================================================
