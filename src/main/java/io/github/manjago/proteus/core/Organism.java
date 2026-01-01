package io.github.manjago.proteus.core;

/**
 * An organism living in the soup.
 * 
 * Each organism has:
 * - A genome (code) stored at startAddr in memory
 * - Its own CPU state (registers, IP)
 * - Lifecycle tracking (age, errors, alive status)
 * - Genealogy (parentId for family tree)
 * - Memory allocId for safe cleanup
 * 
 * In ISA v1.2, organisms are position-independent, so startAddr can be
 * changed during defragmentation without breaking execution.
 */
public class Organism {
    
    private final int id;
    private int startAddr;  // Mutable for defragmentation (v1.2)
    private final int size;
    private final CpuState state;
    private final int parentId;
    private final long birthCycle;
    private int allocId = -1;  // BitmapMM allocId for safe memory cleanup
    
    private boolean alive = true;
    
    /**
     * Create a new organism.
     * 
     * @param id unique organism identifier
     * @param startAddr memory address where genome starts
     * @param size genome size in instructions
     * @param parentId id of parent organism (-1 for Adam)
     * @param birthCycle cycle when organism was spawned
     */
    public Organism(int id, int startAddr, int size, int parentId, long birthCycle) {
        this.id = id;
        this.startAddr = startAddr;
        this.size = size;
        this.parentId = parentId;
        this.birthCycle = birthCycle;
        this.state = new CpuState(startAddr);  // IP starts at organism's code
    }
    
    /**
     * Create a new organism with allocId for safe memory tracking.
     */
    public Organism(int id, int startAddr, int size, int parentId, long birthCycle, int allocId) {
        this(id, startAddr, size, parentId, birthCycle);
        this.allocId = allocId;
    }
    
    // ========== Getters ==========
    
    public int getId() {
        return id;
    }
    
    public int getStartAddr() {
        return startAddr;
    }
    
    /**
     * Update organism's start address (for defragmentation).
     * Also updates the CpuState's startAddr.
     * Safe in ISA v1.2 due to position-independent code.
     * 
     * @param newAddr new memory address
     */
    public void setStartAddr(int newAddr) {
        this.startAddr = newAddr;
        this.state.setStartAddr(newAddr);
    }
    
    public int getSize() {
        return size;
    }
    
    public int getAllocId() {
        return allocId;
    }
    
    /**
     * Set allocId (used after defragmentation when memory is remarked).
     */
    public void setAllocId(int allocId) {
        this.allocId = allocId;
    }
    
    public CpuState getState() {
        return state;
    }
    
    public int getParentId() {
        return parentId;
    }
    
    public long getBirthCycle() {
        return birthCycle;
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    // ========== Lifecycle ==========
    
    /**
     * Mark organism as dead.
     */
    public void kill() {
        this.alive = false;
    }
    
    /**
     * Get the age of the organism (instructions executed).
     * This is taken from CpuState.age which increments each cycle.
     * 
     * @return age in CPU cycles
     */
    public long getAge() {
        return state.getAge();
    }
    
    /**
     * Get the error count.
     * 
     * @return number of execution errors
     */
    public int getErrors() {
        return state.getErrors();
    }
    
    // ========== Object methods ==========
    
    @Override
    public String toString() {
        return String.format("Organism#%d[addr=%d, size=%d, parent=%d, age=%d, %s]",
                id, startAddr, size, parentId, getAge(), alive ? "alive" : "dead");
    }
    
    /**
     * Short string representation for lists.
     */
    public String toShortString() {
        return String.format("#%d[%d]", id, startAddr);
    }
}
