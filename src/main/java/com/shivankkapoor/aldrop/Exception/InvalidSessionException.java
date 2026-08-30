package com.shivankkapoor.aldrop.Exception;

public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Invalid or expired session");
    }
}
