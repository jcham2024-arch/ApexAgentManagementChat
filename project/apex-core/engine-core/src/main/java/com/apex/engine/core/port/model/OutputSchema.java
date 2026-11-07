package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Output schema for tool results, validated by the JSON engine at the
 * gateway boundary before the result reaches the model. A schema violation
 * is a deterministic {@link com.apex.engine.core.port.model.ToolResult}
 * with {@code isError=true}, never an exception. Tier 0.
 */
public record OutputSchema(
        String type,
        Map<String, ObjectSchema> properties,
        List<String> required
) {
    public OutputSchema {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(required, "required");
        properties = Map.copyOf(properties);
        required = List.copyOf(required);
        for (String name : required) {
            if (!properties.containsKey(name)) {
                throw new IllegalArgumentException(
                    "required property " + name + " is not defined");
            }
        }
    }

    /** Schema for a single object property. */
    public record ObjectSchema(
            String type,
            String description,
            List<ObjectSchema> properties,
            List<String> required,
            ObjectSchema items
    ) {
        public ObjectSchema {
            Objects.requireNonNull(type, "type");
            properties = properties == null ? List.of() : List.copyOf(properties);
            required = required == null ? List.of() : List.copyOf(required);
            items = items == null ? null : items;
        }
    }
}
