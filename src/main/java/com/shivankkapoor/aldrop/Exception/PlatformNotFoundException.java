package com.shivankkapoor.aldrop.Exception;

import java.util.UUID;

public class PlatformNotFoundException extends RuntimeException {

    public PlatformNotFoundException(UUID id) {
        super("Platform not found: " + id);
    }
}
