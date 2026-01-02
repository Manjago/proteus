; =============================================================================
; Adam v3 - The First Self-Replicating Organism
; =============================================================================
; 
; This is the "ancestor" from which all life evolves.
; 14 instructions, position-independent code (ISA v1.2).
;
; Strategy:
;   1. Get my address
;   2. Allocate space for child
;   3. Copy my genome to child (with possible mutations)
;   4. Spawn child as independent organism
;   5. Repeat forever
;
; Registers:
;   R0 - Loop counter (0 to SIZE-1)
;   R3 - Child address (from ALLOCATE)
;   R4 - Genome size (14)
;   R5 - Source pointer (absolute, incrementing)
;   R6 - Destination pointer (absolute, incrementing)
;   R7 - My start address (from GETADDR)
;
; =============================================================================

start:
    GETADDR R7          ; R7 = my start address
    MOVI R4, 14         ; R4 = genome size (this organism is 14 instructions)
    ALLOCATE R4, R3     ; Request memory, R3 = child address (or -1 if failed)

; Initialize copy pointers
    MOV R5, R7          ; R5 = source pointer (start of my genome)
    MOV R6, R3          ; R6 = destination pointer (start of child)
    MOVI R0, 0          ; R0 = counter = 0

; Copy loop - this is where MUTATION can happen!
loop:
    COPY R5, R6         ; memory[R6] = memory[R5] (may mutate!)
    INC R5              ; src++
    INC R6              ; dst++
    INC R0              ; counter++
    JLT R0, R4, loop   ; if counter < SIZE, continue loop

; Spawn child and restart
    SPAWN R3, R4        ; Register child as new organism
    MOVI R0, 0          ; Reset counter for next iteration
    JMP start           ; Go back and make another child

; =============================================================================
; Notes:
; - COPY instruction is where evolution happens (mutations)
; - If ALLOCATE fails (returns -1), COPY writes to invalid address -> errors
; - If SPAWN fails, child memory remains allocated but unused
; - Organism runs forever until killed by reaper (age/errors)
; =============================================================================
