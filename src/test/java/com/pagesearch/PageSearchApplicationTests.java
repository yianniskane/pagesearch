package com.pagesearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class PageSearchApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void runsOnJava21() {
        assertEquals(21, Runtime.version().feature());
    }
}
