package com.personalspace.api.exception;

public class DuplicateGroceryItemLabelException extends RuntimeException {
    public DuplicateGroceryItemLabelException(String message) {
        super(message);
    }
}
