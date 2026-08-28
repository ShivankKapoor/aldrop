package com.shivankkapoor.aldrop.Exception;

public class PlatformNameTakenException extends RuntimeException {

    public PlatformNameTakenException(String name) {
        super("Platform name already in use: " + name);
    }
}
