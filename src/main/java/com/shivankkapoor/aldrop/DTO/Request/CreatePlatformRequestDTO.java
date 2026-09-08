package com.shivankkapoor.aldrop.DTO.Request;

import com.shivankkapoor.aldrop.Validation.FieldLimits;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreatePlatformRequestDTO {
    @NotBlank
    @Size(max = FieldLimits.PLATFORM_NAME_MAX)
    private String name;

    private Duration sessionTtl;

    @Positive
    private Integer maxSessionsPerUser;

    private boolean totpAvailable;

    private boolean requireDeviceBinding;
}
