package com.apex.engine.cli;

import com.apex.chat.common.identity.TenantId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed CLI arguments. Extracted from {@code Main} so both stay under
 * the 200-line limit. Throws {@link IllegalArgumentException} on a
 * usage error, which the caller turns into exit code 2.
 */
record CliArgs(boolean real, boolean autoApprove, Path workspace,
               TenantId tenant, Path taskFile) {

    static CliArgs parse(String[] args) {
        boolean real = false;
        boolean autoApprove = false;
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        String tenantStr = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--real")) {
                real = true;
            } else if (a.equals("--auto-approve")) {
                autoApprove = true;
            } else if (a.equals("--workspace")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "--workspace needs a path");
                }
                workspace = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if (a.equals("--tenant")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--tenant needs an id");
                }
                tenantStr = args[++i];
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("unknown flag: " + a);
            } else {
                positional.add(a);
            }
        }
        if (positional.size() != 1) {
            throw new IllegalArgumentException(
                    "expected 1 task file, got " + positional.size());
        }
        TenantId tenant = tenantStr != null ? new TenantId(tenantStr) : null;
        return new CliArgs(real, autoApprove, workspace, tenant,
                Path.of(positional.get(0)));
    }
}
