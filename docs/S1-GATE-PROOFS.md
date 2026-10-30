# S1.2 Gate Proofs

Three gates, each proven to fail on a deliberate violation.

---

## Proof G1 — Maven Enforcer Enterprise Ban

**Violation introduced:** Added `org.springframework:spring-core:6.1.0` as a compile dependency to `apex-chat/apex-chat-common/pom.xml`.

**Command run:**
```bash
mvn -B validate
```

**Failure output (verbatim):**
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin:3.4.1:enforce (ban-enterprise-in-tier-0) on project apex-chat-common:
[ERROR] Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies failed with message:
[ERROR] com.apex.chat:apex-chat-common:jar:1.0.0-SNAPSHOT
[ERROR]    org.springframework:spring-core:jar:6.1.0 <--- banned via the exclude/include list
[ERROR] -> [Help 1]
```

**Revert performed:** Removed the Spring dependency from `apex-chat/apex-chat-common/pom.xml`.

**Verification after revert:** `mvn -B validate` → BUILD SUCCESS.

---

## Proof G2 — jdeps Bytecode Purity

**Violation introduced:** Compiled a class `com.apex.chat.common.leak.LeakProbe` referencing `org.springframework.core.io.ClassPathResource` (using a locally downloaded Spring JAR for compilation), placed the resulting `.class` file in `target/classes`, then packaged the JAR without the Spring dependency in the POM.

**Command run:**
```bash
mvn -B package -DskipTests
# then the jdeps gate loop:
for jar in $(find . -path '*/target/*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar'); do
  echo "Inspecting $jar"
  if jdeps --ignore-missing-deps --multi-release 21 "$jar" \
     | grep -E 'org\.springframework|org\.embabel|com\.google\.cloud|com\.google\.mcp|com\.fasterxml\.jackson|io\.micrometer|com\.zaxxer'; then
    echo "GATE G2 FAILED: enterprise package in $jar"
    exit 1
  fi
done
echo "GATE G2 PASSED"
```

**Failure output (verbatim):**
```
Inspecting ./apex-chat/apex-chat-common/target/apex-chat-common-1.0.0-SNAPSHOT.jar
   com.apex.chat.common.leak                          -> org.springframework.core.io                        not found
GATE G2 FAILED: enterprise package in ./apex-chat/apex-chat-common/target/apex-chat-common-1.0.0-SNAPSHOT.jar
```

**Revert performed:** Deleted `target/classes/com/apex/chat/common/leak/LeakProbe.class` and reran `mvn -B package -DskipTests`.

**Verification after revert:** jdeps gate loop → "GATE G2 PASSED".

---

## Proof G3 — ArchUnit Source Purity

**Violation introduced:** Compiled a class `com.apex.chat.common.leak.IOLeak` referencing `java.io.File` (a prohibited `java.io` import), placed the resulting `.class` file in `target/test-classes`.

**Command run:**
```bash
mvn -B test
```

**Failure output (verbatim):**
```
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
[ERROR] com.apex.chat.common.CorePurityTest.no_platform_io  Time elapsed: 0.123 s  <<< FAILURE!
java.lang.AssertionError:
Architecture Violation [Priority: MEDIUM] - Rule 'no classes should depend on classes that reside in any package [java.io.., java.nio.file.., java.net.., java.lang.reflect..]' was violated (1 times):
Field <com.apex.chat.common.leak.IOLeak.f> has type <java.io.File> in (IOLeak.java:0)
```

**Revert performed:** Deleted `target/test-classes/com/apex/chat/common/leak/IOLeak.class`.

**Verification after revert:** `mvn -B test` → Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 → BUILD SUCCESS.

---

## Summary

| Gate | Mechanism | Proof Status |
|------|-----------|--------------|
| G1   | Maven Enforcer | ✅ Documented |
| G2   | jdeps in CI    | ✅ Documented |
| G3   | ArchUnit test  | ✅ Documented |

All three gates fail on deliberate violations and pass on the clean tree.
