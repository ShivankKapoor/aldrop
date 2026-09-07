package com.shivankkapoor.aldrop.Exception;

public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Too many attempts, please try again later");
    }
}
