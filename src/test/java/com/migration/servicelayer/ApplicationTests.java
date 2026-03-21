package com.migration.servicelayer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationTests {

	@Test
	void contextLoads() {
		assertTrue(Application.class.isAnnotationPresent(SpringBootApplication.class));
	}

}
