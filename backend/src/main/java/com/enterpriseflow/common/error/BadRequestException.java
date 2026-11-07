package com.enterpriseflow.common.error;

/** A business rule rejected the request input, beyond what Bean Validation can express (HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
