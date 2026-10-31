package com.apex.chat.common.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TypedIdTest {

    @Test
    void acceptsNonBlankValue() {
        assertThat(new TypedId<String>("tenant-1").value())
                .isEqualTo("tenant-1");
    }

    @Test
    void acceptsNonBlankValueWithSurroundingWhitespace() {
        assertThat(new TypedId<String>(" id ").value()).isEqualTo(" id ");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TypedId<String>(null))
                .withMessage("TypedId value");
    }

    @Test
    void rejectsBlank() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TypedId<String>("   "))
                .withMessage("TypedId must not be blank");
    }

    @Test
    void equalityIsValueBased() {
        TypedId<String> first = new TypedId<>("tenant-1");
        TypedId<String> second = new TypedId<>("tenant-1");
        TypedId<String> other = new TypedId<>("tenant-2");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(other);
    }
}
