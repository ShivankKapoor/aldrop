package com.shivankkapoor.aldrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AldropApplication {

	public static void main(String[] args) {
		SpringApplication.run(AldropApplication.class, args);
	}

}
