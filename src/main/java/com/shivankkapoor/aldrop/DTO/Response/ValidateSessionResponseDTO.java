package com.shivankkapoor.aldrop.DTO.Response;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateSessionResponseDTO {
    private UUID userId;

    /**
     * The session owner's username. Platforms that keep their own user row use this to provision
     * one on a user's first login, rather than needing a separate lookup.
     */
    private String username;

    private OffsetDateTime expiresAt;
}
