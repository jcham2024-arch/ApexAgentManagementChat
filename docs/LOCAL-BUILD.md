# Local Build Recipe

This project uses Maven with Java 21. Two flags are required for local development:

```bash
export MAVEN_REPO_LOCAL=$PWD/.m2
export Java_IO_TMPDIR=$HARNESS_SCRATCH/apex-tmp
```

## Build Layer 1 (apex-chat)

```bash
cd /data/user/0/com.androidharness.app/files/github-projects/ApexAgentManagementChat-afde9c48
mvn -B -Dmaven.repo.local=$MAVEN_REPO_LOCAL install
```

## Build Engine Reactor (all tiers)

```bash
mvn -B -Dmaven.repo.local=$MAVEN_REPO_LOCAL -DargLine="-Djava.io.tmpdir=$Java_IO_TMPDIR" -f apex/pom.xml test
```

## Verify Gates

- **Gate G2 (jdeps):** `jdeps --ignore-missing-deps --multi-release 21 <jar>` — no banned packages in Tier 0 class sets.
- **Gate G3 (ArchUnit):** `mvn test` — CorePurityTest must pass in every Tier 0 module.

## Notes

- `user.home` points at a Termux path (`/data/data/com.termux/files/home`), so `-Dmaven.repo.local` is required.
- `java.io.tmpdir` is `/data/data/com.termux/files/usr/tmp/` by default, which does not exist for the app UID. The `-DargLine` flag overrides it.
- No `mvn test` can be run locally without both flags; CI is the verifier per AGENT_RULES §5.
