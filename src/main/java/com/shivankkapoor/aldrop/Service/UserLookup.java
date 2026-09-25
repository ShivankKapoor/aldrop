package com.shivankkapoor.aldrop.Service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivankkapoor.aldrop.Cache.CachedUser;
import com.shivankkapoor.aldrop.Cache.DataCache;
import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.Repository.UserRepository;

@Service
public class UserLookup {

    private final DataCache<UUID, CachedUser> cache;
    private final UserRepository userRepository;

    public UserLookup(DataCache<UUID, CachedUser> cache, UserRepository userRepository) {
        this.cache = cache;
        this.userRepository = userRepository;
    }

    public Optional<CachedUser> findActiveById(UUID userId) {
        return Optional.ofNullable(cache.get(userId, id ->
                userRepository.findById(id)
                        .filter(User::isActive)
                        .map(CachedUser::from)
                        .orElse(null)));
    }
}
