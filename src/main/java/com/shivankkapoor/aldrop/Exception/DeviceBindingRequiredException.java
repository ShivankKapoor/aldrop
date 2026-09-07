package com.shivankkapoor.aldrop.Exception;

public class DeviceBindingRequiredException extends RuntimeException {

    public DeviceBindingRequiredException() {
        super("ipAddress and userAgent are required for this platform");
    }
}
