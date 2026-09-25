package com.shivankkapoor.aldrop.Cache;

import java.util.UUID;

import com.shivankkapoor.aldrop.Data.User;

public record CachedUser(UUID id, String username) {

    public static CachedUser from(User user) {
        return new CachedUser(user.getId(), user.getUsername());
    }
}
