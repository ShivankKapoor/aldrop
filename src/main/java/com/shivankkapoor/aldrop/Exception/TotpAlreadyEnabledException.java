package com.shivankkapoor.aldrop.Exception;

public class TotpAlreadyEnabledException extends RuntimeException {

    public TotpAlreadyEnabledException() {
        super("TOTP is already enabled for this user");
    }
}
