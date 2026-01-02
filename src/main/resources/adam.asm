; =============================================================================
; ADAM v3 - The First Self-Replicating Organism (ISA v1.2)
; =============================================================================
;
; This is the "ancestor" from which all other organisms will evolve.
; A minimal self-replicator that copies itself and spawns a child.
;
; Register Usage:
;   R0: counter (0 to size-1)
;   R3: child absolute address (from ALLOCATE)
;   R4: genome size (14 instructions)
;   R5: source pointer (absolute, incremented)
;   R6: destination pointer (absolute, incremented)  
;   R7: my absolute start address (from GETADDR)
;
; Position-Independent Code (v1.2):
;   - Uses GETADDR to get absolute start address
;   - Uses relative JMP/JLT offsets
;   - Can be safely defragmented (moved in memory)
;
; Size: 14 instructions
; =============================================================================

; === Constants and setup ===
        MOVI R4, 14         ; R4 = genome size
        GETADDR R7          ; R7 = my absolute start address
        
allocate:
        ALLOCATE R4, R3     ; R3 = child's absolute address

; === Prepare pointers ===
        MOV R5, R7          ; R5 = src pointer (my start)
        MOV R6, R3          ; R6 = dst pointer (child start)
        MOVI R0, 0          ; R0 = counter

; === Copy loop ===
copy_loop:
        COPY R5, R6         ; memory[R6] = memory[R5] (may mutate!)
        INC R5              ; src++
        INC R6              ; dst++
        INC R0              ; counter++
        JLT R0, R4, copy_loop  ; if (R0 < R4) continue copying

; === Spawn child and repeat ===
        SPAWN R3, R4        ; Create child organism at R3 with size R4
        MOVI R0, 0          ; Reset counter for next iteration
        JMP allocate        ; Jump back to allocate next child
