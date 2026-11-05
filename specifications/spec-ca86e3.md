## Files

**pom.xml**
```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  ...
</project>
```

**kernel/pom.xml**
```xml
<project>
  ...
</project>
```

**kernel/kernel-core/src/main/java/com/agentplatform/kernel/core/id/TenantId.java**
```java
package com.agentplatform.kernel.core.id;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "TenantId");
    }
}
```