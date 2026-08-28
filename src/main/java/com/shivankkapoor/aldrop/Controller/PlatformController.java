package com.shivankkapoor.aldrop.Controller;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.DTO.Request.CreatePlatformRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.CreatePlatformResponseDTO;
import com.shivankkapoor.aldrop.Service.PlatformService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
