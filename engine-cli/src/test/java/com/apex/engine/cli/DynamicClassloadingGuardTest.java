package com.apex.engine.cli;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class DynamicClassloadingGuardTest {

    @Test
    void noDefineClassInMainSources() throws Exception {
        Path mainSrc = Path.of("engine-core/src/main/java");
        if (!Files.exists(mainSrc)) return;
        for (File f : mainSrc.toFile().listFiles()) {
            if (f == null || !f.isFile()) continue;
            String content = new String(Files.readAllBytes(f.toPath()));
            assertThat(content).doesNotContain("defineClass");
        }
    }

    @Test
    void noUrlClassloaderInMainSources() throws Exception {
        Path mainSrc = Path.of("engine-core/src/main/java");
        if (!Files.exists(mainSrc)) return;
        for (File f : mainSrc.toFile().listFiles()) {
            if (f == null || !f.isFile()) continue;
            String content = new String(Files.readAllBytes(f.toPath()));
            assertThat(content).doesNotContain("URLClassLoader");
        }
    }

    @Test
    void noClassForNameInMainSources() throws Exception {
        Path mainSrc = Path.of("engine-core/src/main/java");
        if (!Files.exists(mainSrc)) return;
        for (File f : mainSrc.toFile().listFiles()) {
            if (f == null || !f.isFile()) continue;
            String content = new String(Files.readAllBytes(f.toPath()));
            assertThat(content).doesNotContain("Class.forName");
        }
    }
}
