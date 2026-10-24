package org.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

    private Compiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new Compiler("test input");
    }

    @Test
    void emptyText() {
        String result = compiler.compile();
        assertEquals("", result);
    }

    @Test
    void notEmptyString() {
        String result = compiler.compile();
        assertNotEquals("", result);
    }

}