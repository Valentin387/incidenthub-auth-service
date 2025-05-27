package com.incidenthub.auth;

import org.springframework.boot.SpringApplication;

public class TestIncidentHubAuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(IncidentHubAuthServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
