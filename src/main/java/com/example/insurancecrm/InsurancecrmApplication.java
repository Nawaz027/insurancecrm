package com.example.insurancecrm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InsurancecrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsurancecrmApplication.class, args);
	}

}
