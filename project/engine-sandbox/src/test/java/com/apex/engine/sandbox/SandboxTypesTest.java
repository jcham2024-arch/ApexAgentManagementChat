package com.apex.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SandboxTypesTest {

    @Test
    void sandboxCommandRejectsNullBinaryPath() {
        assertThatThrownBy(() -> new SandboxCommand(null, List.of("a"), Path.of("/tmp"), 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sandboxCommandRejectsNullArgs() {
        assertThatThrownBy(() -> new SandboxCommand(Path.of("/bin/echo"), null, Path.of("/tmp"), 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sandboxCommandRejectsNullCwd() {
        assertThatThrownBy(() -> new SandboxCommand(Path.of("/bin/echo"), List.of("a"), null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sandboxCommandRejectsRelativeBinaryPath() {
        assertThatThrownBy(() -> new SandboxCommand(Path.of("bin/echo"), List.of("a"), Path.of("/tmp"), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sandboxCommandRejectsRelativeCwd() {
        assertThatThrownBy(() -> new SandboxCommand(Path.of("/bin/echo"), List.of("a"), Path.of("relative"), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sandboxCommandRejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new SandboxCommand(Path.of("/bin/echo"), List.of("a"), Path.of("/tmp"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SandboxCommand(Path.of("/bin/echo"), List.of("a"), Path.of("/tmp"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sandboxCommandDefensivelyCopiesArgs() {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("a");
        SandboxCommand cmd = new SandboxCommand(Path.of("/bin/echo"), args, Path.of("/tmp"), 10);
        args.add("b");
        assertThat(cmd.args()).containsExactly("a");
        assertThatThrownBy(() -> cmd.args().add("c"))
                .isInstanceOf(java.lang.UnsupportedOperationException.class);
    }

    @Test
    void sandboxResultRejectsNullOutput() {
        assertThatThrownBy(() -> new SandboxResult(0, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void sandboxResultAcceptsAnyExitCode() {
        SandboxResult result = new SandboxResult(-1, "error output", true);
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.output()).isEqualTo("error output");
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void sandboxTierHasExactlyThreeValues() {
        assertThat(SandboxTier.values()).containsExactly(
                SandboxTier.PROCESS, SandboxTier.CONTAINER, SandboxTier.FIRE_CRACKER);
    }
}
