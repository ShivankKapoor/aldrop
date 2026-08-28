package com.shivankkapoor.aldrop.DTO.Request;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreatePlatformRequestDTO {
    @NotBlank
    private String name;

    private Duration sessionTtl;

    @Positive
    private Integer maxSessionsPerUser;

    private boolean totpAvailable;

    private boolean requireDeviceBinding;
}
