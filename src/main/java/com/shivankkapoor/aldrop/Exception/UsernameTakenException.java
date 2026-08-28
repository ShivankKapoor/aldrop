package com.shivankkapoor.aldrop.Exception;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username already in use: " + username);
    }
}
