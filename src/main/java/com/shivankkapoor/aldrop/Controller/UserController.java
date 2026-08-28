package com.shivankkapoor.aldrop.Controller;

import java.util.UUID;

import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.RegisterUserRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.RegisterUserResponseDTO;
import com.shivankkapoor.aldrop.Filter.PlatformApiKeyAuthFilter;
import com.shivankkapoor.aldrop.Service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/auth")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponseDTO> register(
            @Valid @RequestBody RegisterUserRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        log.info("User registration requested, platformId={}, username={}", platformId, request.getUsername());
        User user = userService.register(platformId, request);

        RegisterUserResponseDTO response = new RegisterUserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
