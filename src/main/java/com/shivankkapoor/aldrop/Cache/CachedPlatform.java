package com.shivankkapoor.aldrop.Cache;

import java.util.UUID;

import com.shivankkapoor.aldrop.Data.Platform;

public record CachedPlatform(UUID id, boolean active, boolean requireDeviceBinding) {

    public static CachedPlatform from(Platform platform) {
        return new CachedPlatform(platform.getId(), platform.isActive(), platform.isRequireDeviceBinding());
    }
}
