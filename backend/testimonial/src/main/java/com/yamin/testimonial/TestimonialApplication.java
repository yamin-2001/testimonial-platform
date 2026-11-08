package com.yamin.testimonial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TestimonialApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestimonialApplication.class, args);
	}

}
