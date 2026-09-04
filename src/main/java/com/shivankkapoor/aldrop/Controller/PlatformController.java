package com.shivankkapoor.aldrop.Controller;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.DTO.Request.CreatePlatformRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.UpdatePlatformStatusRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.CreatePlatformResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.UpdatePlatformStatusResponseDTO;
import com.shivankkapoor.aldrop.Service.PlatformService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/platform")
public class PlatformController {

    private static final Logger log = LoggerFactory.getLogger(PlatformController.class);

    @Autowired
    private PlatformService platformService;

    @PostMapping("/create")
    public ResponseEntity<CreatePlatformResponseDTO> create(@Valid @RequestBody CreatePlatformRequestDTO request){
        log.info("Create platform requested, name={}", request.getName());
        Platform platform = platformService.create(request);

        CreatePlatformResponseDTO response = new CreatePlatformResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.getApiKey(),
                platform.getSessionTtl().getSeconds(),
                platform.getMaxSessionsPerUser(),
                platform.isTotpAvailable(),
                platform.isRequireDeviceBinding()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UpdatePlatformStatusResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlatformStatusRequestDTO request){
        log.info("Update platform status requested, id={}, isActive={}", id, request.getIsActive());
        Platform platform = platformService.updateActiveStatus(id, request.getIsActive());

        UpdatePlatformStatusResponseDTO response = new UpdatePlatformStatusResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.isActive()
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        log.info("Delete platform requested, id={}", id);
        platformService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
