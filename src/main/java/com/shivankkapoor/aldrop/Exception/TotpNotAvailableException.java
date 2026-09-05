package com.shivankkapoor.aldrop.Exception;

public class TotpNotAvailableException extends RuntimeException {

    public TotpNotAvailableException() {
        super("TOTP is not available on this platform");
    }
}
