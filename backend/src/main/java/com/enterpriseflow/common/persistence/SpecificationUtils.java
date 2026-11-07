package com.enterpriseflow.common.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.Locale;

public final class SpecificationUtils {

    private static final char ESCAPE = '\\';

    private SpecificationUtils() {
    }

    /**
     * Case-insensitive "contains" that escapes LIKE wildcards, so a search for "50%" matches the literal text
     * instead of every value starting with "50".
     */
    public static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> field, String term) {
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + escaped + "%", ESCAPE);
    }
}
