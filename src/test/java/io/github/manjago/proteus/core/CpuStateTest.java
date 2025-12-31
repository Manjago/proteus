package io.github.manjago.proteus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CpuState (ISA v1.2).
 * 
 * In v1.2, CpuState has:
 * - startAddr: absolute address of organism in memory
 * - ip: RELATIVE offset from startAddr (not absolute!)
 * - absoluteIp = startAddr + ip
 */
class CpuStateTest {

    @Test
    @DisplayName("New state has all registers at zero")
    void newStateHasZeroRegisters() {
        CpuState state = new CpuState();
        
        for (int i = 0; i < CpuState.REGISTER_COUNT; i++) {
            assertEquals(0, state.getRegister(i));
        }
    }

    @Test
    @DisplayName("New state has IP at zero")
    void newStateHasZeroIp() {
        CpuState state = new CpuState();
        assertEquals(0, state.getIp());
    }
    
    @Test
    @DisplayName("New state has startAddr at zero")
    void newStateHasZeroStartAddr() {
        CpuState state = new CpuState();
        assertEquals(0, state.getStartAddr());
    }

    @Test
    @DisplayName("Constructor with startAddr sets startAddr, IP stays 0 (v1.2)")
    void constructorWithStartAddr() {
        CpuState state = new CpuState(100);
        
        assertEquals(100, state.getStartAddr());
        assertEquals(0, state.getIp());  // IP is relative, starts at 0
        assertEquals(100, state.getAbsoluteIp());  // absolute = startAddr + ip
    }
    
    @Test
    @DisplayName("getAbsoluteIp returns startAddr + ip (v1.2)")
    void absoluteIpCalculation() {
        CpuState state = new CpuState(200);
        state.setIp(5);
        
        assertEquals(200, state.getStartAddr());
        assertEquals(5, state.getIp());
        assertEquals(205, state.getAbsoluteIp());
    }

    @Test
    @DisplayName("Copy constructor creates independent copy")
    void copyConstructor() {
        CpuState original = new CpuState(50);
        original.setIp(10);
        original.setRegister(0, 42);
        original.incrementErrors();
        original.incrementAge();
        
        CpuState copy = new CpuState(original);
        
        // Verify copy has same values
        assertEquals(50, copy.getStartAddr());
        assertEquals(10, copy.getIp());
        assertEquals(42, copy.getRegister(0));
        assertEquals(1, copy.getErrors());
        assertEquals(1, copy.getAge());
        
        // Modify original, verify copy is independent
        original.setRegister(0, 100);
        original.setIp(200);
        original.setStartAddr(999);
        
        assertEquals(42, copy.getRegister(0));
        assertEquals(10, copy.getIp());
        assertEquals(50, copy.getStartAddr());
    }

    @Test
    @DisplayName("Register index is masked to 0-7")
    void registerIndexMasked() {
        CpuState state = new CpuState();
        
        state.setRegister(8, 42);  // Should set R0 (8 & 7 = 0)
        assertEquals(42, state.getRegister(0));
        assertEquals(42, state.getRegister(8));
        
        state.setRegister(15, 99); // Should set R7 (15 & 7 = 7)
        assertEquals(99, state.getRegister(7));
    }

    @Test
    @DisplayName("advanceIp increments relative IP by 1")
    void advanceIp() {
        CpuState state = new CpuState(100);  // startAddr=100
        
        assertEquals(0, state.getIp());
        
        state.advanceIp();
        assertEquals(1, state.getIp());
        assertEquals(101, state.getAbsoluteIp());
        
        state.advanceIp();
        assertEquals(2, state.getIp());
        assertEquals(102, state.getAbsoluteIp());
    }
    
    @Test
    @DisplayName("jumpRelative adds offset to IP (v1.2)")
    void jumpRelative() {
        CpuState state = new CpuState(100);
        state.setIp(10);
        
        state.jumpRelative(5);
        assertEquals(15, state.getIp());
        
        state.jumpRelative(-10);
        assertEquals(5, state.getIp());
    }

    @Test
    @DisplayName("Error counting works")
    void errorCounting() {
        CpuState state = new CpuState();
        
        assertEquals(0, state.getErrors());
        
        state.incrementErrors();
        assertEquals(1, state.getErrors());
        
        state.incrementErrors();
        assertEquals(2, state.getErrors());
        
        state.resetErrors();
        assertEquals(0, state.getErrors());
    }

    @Test
    @DisplayName("Age tracking works")
    void ageTracking() {
        CpuState state = new CpuState();
        
        assertEquals(0, state.getAge());
        
        state.incrementAge();
        assertEquals(1, state.getAge());
        
        for (int i = 0; i < 100; i++) {
            state.incrementAge();
        }
        assertEquals(101, state.getAge());
    }

    @Test
    @DisplayName("Reset clears IP, errors, age but preserves startAddr")
    void resetClearsEverything() {
        CpuState state = new CpuState(100);
        state.setIp(50);
        state.setRegister(0, 42);
        state.setRegister(7, 99);
        state.incrementErrors();
        state.incrementAge();
        
        state.reset();
        
        assertEquals(0, state.getIp());
        assertEquals(0, state.getErrors());
        assertEquals(0, state.getAge());
        for (int i = 0; i < CpuState.REGISTER_COUNT; i++) {
            assertEquals(0, state.getRegister(i));
        }
        // startAddr is preserved
        assertEquals(100, state.getStartAddr());
    }

    @Test
    @DisplayName("clearRegisters only clears registers")
    void clearRegistersOnly() {
        CpuState state = new CpuState(100);
        state.setIp(20);
        state.setRegister(0, 42);
        state.incrementErrors();
        state.incrementAge();
        
        state.clearRegisters();
        
        // Registers cleared
        assertEquals(0, state.getRegister(0));
        
        // Other state preserved
        assertEquals(20, state.getIp());
        assertEquals(100, state.getStartAddr());
        assertEquals(1, state.getErrors());
        assertEquals(1, state.getAge());
    }

    @Test
    @DisplayName("toString shows startAddr and both IPs (v1.2)")
    void toStringReadable() {
        CpuState state = new CpuState(100);
        state.setIp(5);
        state.setRegister(0, 42);
        
        String str = state.toString();
        
        assertTrue(str.contains("startAddr=100"));
        assertTrue(str.contains("IP=5"));
        assertTrue(str.contains("abs=105"));
        assertTrue(str.contains("42"));   // R0 value
    }
    
    @Test
    @DisplayName("setStartAddr works for defragmentation (v1.2)")
    void setStartAddrForDefrag() {
        CpuState state = new CpuState(100);
        state.setIp(5);
        
        // Simulate defragmentation: move organism to new address
        state.setStartAddr(500);
        
        // IP stays the same (relative), but absolute changes
        assertEquals(5, state.getIp());
        assertEquals(500, state.getStartAddr());
        assertEquals(505, state.getAbsoluteIp());
    }
}
