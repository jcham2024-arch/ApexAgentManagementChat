package com.apex.engine.tools.impl;

import java.util.Map;

/**
 * A single kernel tool. Implementations are stateless per call: all
 * configuration is captured at construction, and {@link #execute}
 * derives everything else from {@code args}.
 */
public interface KernelTool {
    String name();
    String execute(Map<String, Object> args);
}
