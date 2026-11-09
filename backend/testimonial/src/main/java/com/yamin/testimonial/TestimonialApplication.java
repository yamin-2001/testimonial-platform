package com.yamin.testimonial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// The scheduled cleanup of expired tokens and handled Stripe events needs
// this; without it the @Scheduled methods are never called.
@EnableScheduling
public class TestimonialApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestimonialApplication.class, args);
	}

}
