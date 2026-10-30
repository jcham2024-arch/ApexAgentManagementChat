package com.apex.chat.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class CorePurityTest {

    private static final JavaClasses CORE =
            new ClassFileImporter().importPackages("com.apex.chat.common");

    @Test
    void no_enterprise_imports() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.embabel..",
                        "com.google.cloud..",
                        "com.google.mcp..",
                        "com.fasterxml.jackson..",
                        "io.micrometer..",
                        "com.zaxxer..");
        rule.check(CORE);
    }

    @Test
    void no_platform_io() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..",
                        "java.nio.file..",
                        "java.net..",
                        "java.lang.reflect..");
        rule.check(CORE);
    }

    @Test
    void no_executors() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.concurrent.Executors");
        rule.check(CORE);
    }
}
