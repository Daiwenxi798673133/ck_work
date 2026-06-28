package org.example.flow.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FlowRepositoryTest {

    @Autowired
    private FlowRepository flowRepository;

    @Test
    void ping_returnsOne() {
        assertEquals(1, flowRepository.ping());
    }
}
