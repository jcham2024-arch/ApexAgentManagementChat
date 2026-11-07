package com.apex.chat.common.identity;
import java.util.Objects;

/**
 * Shared validation for identity records. All identity POJOs delegate to
 * this helper so that null/blank checks live in one place rather than
 * being repeated per record.
 *
 * <p>Tier 0: no behavior beyond null/blank validation. No threads, no I/O,
 * no network.
 */
public final class IdValidation {

    private IdValidation() {}

    /**
     * Validates that {@code value} is non-null and non-blank, then
     * returns it unchanged.
     *
     * @param value the string value to validate
     * @param name  the field or parameter name, used in error messages
     * @return the value itself, for chaining in compact constructors
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
