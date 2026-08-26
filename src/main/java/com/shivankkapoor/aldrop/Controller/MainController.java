package com.shivankkapoor.aldrop.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @GetMapping("/")
    public ResponseEntity<String> home(){
        log.info("Home endpoint has been called");
        final String homeString = "<html><body><h2>Welcome to Aldrop!</h2></body></html>";
        return ResponseEntity.ok(homeString);
    }
}
