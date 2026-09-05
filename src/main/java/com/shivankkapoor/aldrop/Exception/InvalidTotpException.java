package com.shivankkapoor.aldrop.Exception;

public class InvalidTotpException extends RuntimeException {

    public InvalidTotpException() {
        super("Invalid or expired TOTP challenge");
    }
}
