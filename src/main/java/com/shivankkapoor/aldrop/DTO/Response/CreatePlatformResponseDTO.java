package com.shivankkapoor.aldrop.DTO.Response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlatformResponseDTO {
    private UUID id;
    private String name;
    private String apiKey;
    private long sessionTtlSeconds;
    private Integer maxSessionsPerUser;
    private boolean totpAvailable;
    private boolean requireDeviceBinding;
}
