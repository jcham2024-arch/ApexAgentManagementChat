package com.apex.chat.core.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdGeneratorTest {

    @Test
    void generatesNonNullIds() {
        IdGenerator generator = () -> java.util.UUID.randomUUID().toString();

        String id1 = generator.nextId();
        String id2 = generator.nextId();

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void sequentialGeneratorProducesUniqueIds() {
        IdGenerator generator = new IdGenerator() {
            private int count = 0;

            @Override
            public String nextId() {
                return "id-" + ++count;
            }
        };

        assertThat(generator.nextId()).isEqualTo("id-1");
        assertThat(generator.nextId()).isEqualTo("id-2");
        assertThat(generator.nextId()).isEqualTo("id-3");
    }
}
