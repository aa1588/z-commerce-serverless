package com.zcommerce.shared;

import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SimplePropertyTest {

    @Property
    void simpleTest(@ForAll int number) {
        assertTrue(number == number);
    }
}