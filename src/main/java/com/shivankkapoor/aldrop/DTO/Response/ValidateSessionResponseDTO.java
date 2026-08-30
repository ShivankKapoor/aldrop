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
    private OffsetDateTime expiresAt;
}
