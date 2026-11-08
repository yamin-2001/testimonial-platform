package com.yamin.testimonial.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs integration tests against a real PostgreSQL instance.
 *
 * <p>The schema is managed by Flyway and validated by Hibernate
 * ({@code ddl-auto: validate}), so these tests need a genuine PostgreSQL
 * rather than an in-memory substitute: the schema uses {@code jsonb},
 * {@code inet}, {@code smallint}, {@code uuid} and {@code timestamptz}, and
 * none of those behave the same way in H2.
 *
 * <p>The container is started once and shared by every test class. Using
 * {@code @Container} per class would start a separate container for each one
 * and stop it when that class's context closes, leaving later classes
 * pointing at a dead port. A single manually-managed instance avoids that.
 */
public abstract class PostgresTestContainer {

	private static final PostgreSQLContainer<?> POSTGRES = createContainer();

	private static PostgreSQLContainer<?> createContainer() {
		PostgreSQLContainer<?> container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
				.withDatabaseName("testimonial")
				.withUsername("testimonial")
				.withPassword("testimonial");
		container.start();
		Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
		return container;
	}

	/**
	 * Points the datasource at the shared container.
	 *
	 * <p>This also makes Flyway apply the real migrations, so a test that
	 * passes here proves the migration and the entity mappings agree.
	 */
	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
