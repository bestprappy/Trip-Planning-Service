package com.navio.tripplanningservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TripPlanningServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TripPlanningServiceApplication.class, args);
	}

}
