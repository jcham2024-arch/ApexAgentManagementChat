package com.apex.engine.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionCheckerTest {

    @TempDir
    Path workspace;

    AssertionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AssertionChecker();
    }

    @Test
    void fileExistsPassesWhenPresent() throws IOException {
        Files.writeString(workspace.resolve("out.md"), "x");

        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("file_exists:out.md")));

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Pass.class);
    }

    @Test
    void fileExistsFailsWhenMissing() {
        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("file_exists:absent.md")));

        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Fail.class);
        assertThat(checker.allPassed(results)).isFalse();
    }

    @Test
    void fileContainsPassesWhenPresent() throws IOException {
        Files.writeString(workspace.resolve("out.md"), "Apex is here");

        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("file_contains:out.md:Apex")));

        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Pass.class);
    }

    @Test
    void fileContainsFailsWhenAbsent() throws IOException {
        Files.writeString(workspace.resolve("out.md"), "nothing here");

        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("file_contains:out.md:Apex")));

        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Fail.class);
    }

    @Test
    void unknownPrefixWarnsInsteadOfFailing() {
        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("magic:whatever")));

        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Warn.class);
        assertThat(checker.allPassed(results)).isTrue();
    }

    @Test
    void allPassedIsFalseWhenAnyCheckFails() {
        List<AssertionChecker.CheckResult> results = List.of(
                new AssertionChecker.CheckResult.Pass("a"),
                new AssertionChecker.CheckResult.Warn("b", "w"),
                new AssertionChecker.CheckResult.Fail("c", "f"));

        assertThat(checker.allPassed(results)).isFalse();
    }

    @Test
    void outputFilesAreCheckedWithoutAssertions() throws IOException {
        Files.writeString(workspace.resolve("one.md"), "x");

        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of("one.md", "two.md"), List.of()));

        assertThat(results).hasSize(2);
        assertThat(checker.allPassed(results)).isFalse();
    }

    @Test
    void malformedFileContainsWarns() {
        List<AssertionChecker.CheckResult> results = checker.check(workspace,
                task(List.of(), List.of("file_contains:out.md")));

        assertThat(results.get(0)).isInstanceOf(
                AssertionChecker.CheckResult.Warn.class);
    }

    private static TaskDefinition task(List<String> outputFiles,
                                       List<String> assertions) {
        return new TaskDefinition("t", "d", "i", 3, "m", List.of("read_file"),
                List.of(), outputFiles, assertions);
    }
}
