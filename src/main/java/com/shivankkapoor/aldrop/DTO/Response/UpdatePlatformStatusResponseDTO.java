package com.shivankkapoor.aldrop.DTO.Response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlatformStatusResponseDTO {
    private UUID id;
    private String name;
    private boolean isActive;
}
