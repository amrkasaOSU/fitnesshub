package com.fitnesshub.common.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException accessDenied() {
        return new ForbiddenException("You do not have access to this resource.");
    }
}
