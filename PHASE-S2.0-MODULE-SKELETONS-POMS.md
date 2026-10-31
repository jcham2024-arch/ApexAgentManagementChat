# PHASE-S2.0-MODULE-SKELETONS-POMS.md — Session Package, Part 2 of 3

**Phase:** S2 (coding kernel), sub-phase S2.0 (module skeletons).
**Part 2 of 3:** files to create or modify — POM work, §3.1–§3.5.

Parts: `PHASE-S2.0-MODULE-SKELETONS-SESSION.md` (protocol, deltas,
scope) · `PHASE-S2.0-MODULE-SKELETONS-POMS.md` (this file) ·
`PHASE-S2.0-MODULE-SKELETONS-MODULES.md` (§3.6–§3.9, exit criteria,
commits).

---
## 3. Files to create or modify

All paths are relative to the repo root. Do not create files not listed here.

### 3.1 Modify `apex/pom.xml`

In `<modules>`, add:

```xml
<module>apex-core</module>
<module>engine-tools-impl</module>
<module>engine-llm-http</module>
<module>engine-store-sqlite</module>
<module>engine-cli</module>
```

Remove the **entire** `<plugin>` block for `maven-enforcer-plugin` from `<build><plugins>`. It moves to `apex-chat/pom.xml` and `apex-core/pom.xml`. Leave `<pluginManagement>` unchanged.

### 3.2 Modify `apex-chat/pom.xml`

Add the `maven-enforcer-plugin` block (moved from `apex/pom.xml`) under `<build><plugins>`. It is verbatim:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.4.1</version>
    <executions>
        <execution>
            <id>ban-enterprise-in-tier-0</id>
            <phase>validate</phase>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <bannedDependencies>
                        <excludes>
                            <exclude>org.springframework*</exclude>
                            <exclude>org.springframework.boot*</exclude>
                            <exclude>org.springframework.ai*</exclude>
                            <exclude>com.google.cloud*</exclude>
                            <exclude>com.google.mcp*</exclude>
                            <exclude>org.embabel*</exclude>
                            <exclude>com.fasterxml.jackson*</exclude>
                            <exclude>io.micrometer*</exclude>
                            <exclude>com.zaxxer*</exclude>
                        </excludes>
                        <searchTransitive>true</searchTransitive>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3.3 Create `apex-core/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>apex-core</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>engine-core</module>
        <module>engine-planner</module>
        <module>engine-tools</module>
        <module>engine-store</module>
    </modules>
    <build>
        <plugins>
            <!-- The same maven-enforcer-plugin block as apex-chat/pom.xml,
                 verbatim. -->
        </plugins>
    </build>
</project>
```

### 3.4 Create four Tier 0 module POMs

For each of `apex-core/engine-core/pom.xml`, `apex-core/engine-planner/pom.xml`, `apex-core/engine-tools/pom.xml`, `apex-core/engine-store/pom.xml`:

- Parent: `apex-core`
- `<artifactId>` matches the module directory name
- Dependencies: `org.jspecify:jspecify`, `org.junit.jupiter:junit-jupiter` (test), `org.assertj:assertj-core` (test)
- No `<build>` section (inherits the Enforcer from `apex-core`)

Example for `apex-core/engine-core/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>engine-core</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Repeat with the correct `<artifactId>` for `engine-planner`, `engine-tools`, `engine-store`.

### 3.5 Create four Tier 1 module POMs

For each of `engine-tools-impl/pom.xml`, `engine-llm-http/pom.xml`, `engine-store-sqlite/pom.xml`, `engine-cli/pom.xml`:

- Parent: `apex` (the root POM)
- `<artifactId>` matches the module directory name
- Dependencies: `org.jspecify:jspecify`, `org.junit.jupiter:junit-jupiter` (test), `org.assertj:assertj-core` (test), and a dependency on `apex-core/engine-core` (version `${project.version}`)
- `<build><plugins>` containing the same `maven-enforcer-plugin` block as `apex-chat/pom.xml`, verbatim

Example for `engine-cli/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>engine-cli</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.apex</groupId>
            <artifactId>engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <!-- The same maven-enforcer-plugin block as apex-chat/pom.xml,
                 verbatim. -->
        </plugins>
    </build>
</project>
```

Repeat with the correct `<artifactId>` for `engine-tools-impl`, `engine-llm-http`, `engine-store-sqlite`.

**Note:** `engine-llm-http` declares the dependency on `engine-core` even though it has nothing to use yet. This is per context note §1.7. The dependency is unused until S2.1 populates `ChatModelPort`.
