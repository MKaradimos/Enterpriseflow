package com.enterpriseflow.common.error;

/**
 * The resource does not exist, or exists but the caller is not allowed to know about it.
 * Returning 404 in both cases prevents ids from being probed (insecure direct object references).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s with id '%s' was not found.".formatted(resource, id));
    }
}
