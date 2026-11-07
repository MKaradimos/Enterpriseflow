package com.enterpriseflow.common.api;

import com.enterpriseflow.common.error.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pageables {

    private Pageables() {
    }

    /**
     * Restricts client-supplied sort properties to an explicit allowlist.
     * Without it, {@code ?sort=passwordHash} or a nested path would be passed straight into the query.
     */
    public static Pageable restrictSort(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Sorting by '%s' is not supported. Allowed: %s"
                        .formatted(order.getProperty(), allowedProperties));
            }
        }
        return pageable;
    }
}
