package com.task.userauth.common;

/** A registration clashes with an existing username or email. */
public class DuplicateUserException extends RuntimeException {

    private final String field;

    /**
     * @param field the request field that clashed ({@code username} or {@code email}), or null if unknown
     */
    public DuplicateUserException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
