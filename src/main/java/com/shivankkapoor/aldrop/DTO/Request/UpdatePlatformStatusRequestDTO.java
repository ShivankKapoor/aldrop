package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePlatformStatusRequestDTO {
    @NotNull
    private Boolean isActive;
}
