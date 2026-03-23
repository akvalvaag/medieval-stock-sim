package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NameGeneratorTest {

    @Test
    void generatedNameIsNotEmpty() {
        NameGenerator gen = new NameGenerator();
        assertThat(gen.generate()).isNotBlank();
    }

    @Test
    void generatedNameHasAtLeastTwoWords() {
        NameGenerator gen = new NameGenerator();
        assertThat(gen.generate().split("\\s+").length).isGreaterThanOrEqualTo(2);
    }

    @Test
    void generatesDistinctNamesOverTime() {
        NameGenerator gen = new NameGenerator();
        long distinct = java.util.stream.IntStream.range(0, 50)
            .mapToObj(i -> gen.generate())
            .distinct()
            .count();
        assertThat(distinct).isGreaterThan(5);
    }
}
