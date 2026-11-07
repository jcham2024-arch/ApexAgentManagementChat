# engine-tools-impl

**Tier:** 1 · **Purpose:** Five kernel tools and the default ToolExecutorPort.

## Allowed dependencies
java.base, java.net.http, jspecify

## Ports consumed
none

## Ports implemented
ToolExecutorPort (via KernelToolExecutor)

## Tools
- read_file — UTF-8 read, capped at 50 KB
- write_file — UTF-8 write, parent must exist inside workspace
- list_files — glob match, depth 1
- run_command — allowlisted binary, tokenized argv, 30s default / 120s max, tree kill on timeout
- http_get — public or loopback, link-local blocked

## Tests
JsonTest · RealPathJailTest · ReadFileToolTest · WriteFileToolTest ·
ListFilesToolTest · RunCommandToolTest (K2 + K5) · HttpGetToolTest ·
KernelToolExecutorTest
