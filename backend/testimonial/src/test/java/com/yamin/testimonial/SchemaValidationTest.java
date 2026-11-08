package com.yamin.testimonial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.yamin.testimonial.support.PostgresTestContainer;

/**
 * Verifies that the Flyway-managed schema and the JPA entity mappings agree.
 *
 * <p>The application runs with {@code ddl-auto: validate}, so a mismatch
 * between the migration and the entities prevents the application context
 * from starting at all. This test makes that failure explicit and checks the
 * schema's shape directly, which a context-load test alone would not.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaValidationTest extends PostgresTestContainer {

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("the application context starts, so Flyway's schema matches the entity mappings")
	void contextLoadsAgainstMigratedSchema() {
		// Reaching this point means Hibernate validated every entity against
		// the schema Flyway applied.
		assertThat(dataSource).isNotNull();
	}

	@Test
	@DisplayName("Flyway applied the initial migration successfully")
	void initialMigrationIsApplied() throws SQLException {
		List<String> versions = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery(
						"SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank")) {
			while (rows.next()) {
				versions.add(rows.getString(1));
			}
		}
		assertThat(versions).contains("1");
	}

	@Test
	@DisplayName("every table the modules rely on exists")
	void expectedTablesExist() throws SQLException {
		assertThat(tableNames()).contains("app_users", "email_verification_tokens", "password_reset_tokens",
				"spaces", "testimonials", "subscriptions", "processed_stripe_events");
	}

	@Test
	@DisplayName("the column types Hibernate is sensitive to are exactly as mapped")
	void columnTypesMatchEntityMappings() throws SQLException {
		// rating is mapped as Short, so the column must be smallint and not integer.
		assertThat(columnType("testimonials", "rating")).isEqualTo("smallint");
		// message must be text; @Lob would have expected a large object (oid).
		assertThat(columnType("testimonials", "message")).isEqualTo("text");
		// social_links and the space config columns are jsonb.
		assertThat(columnType("testimonials", "social_links")).isEqualTo("jsonb");
		assertThat(columnType("spaces", "form_config")).isEqualTo("jsonb");
		assertThat(columnType("spaces", "embed_config")).isEqualTo("jsonb");
		assertThat(columnType("spaces", "public_profile_fields")).isEqualTo("jsonb");
		// submitter_ip is inet, mapped as InetAddress.
		assertThat(columnType("testimonials", "submitter_ip")).isEqualTo("inet");
		// The plan/status enums are stored as text, not ordinals.
		assertThat(columnType("subscriptions", "plan")).isEqualTo("character varying");
		assertThat(columnType("subscriptions", "status")).isEqualTo("character varying");
		// Timestamps are timestamptz so that UTC daily buckets are exact.
		assertThat(columnType("testimonials", "created_at")).isEqualTo("timestamp with time zone");
	}

	@Test
	@DisplayName("the token tables are immutable and carry no updated_at column")
	void tokenTablesHaveNoUpdatedAt() throws SQLException {
		assertThat(columnNames("email_verification_tokens")).doesNotContain("updated_at");
		assertThat(columnNames("password_reset_tokens")).doesNotContain("updated_at");
		// Editable tables do carry one.
		assertThat(columnNames("app_users")).contains("updated_at");
		assertThat(columnNames("spaces")).contains("updated_at");
		assertThat(columnNames("testimonials")).contains("updated_at");
	}

	@Test
	@DisplayName("email uniqueness is case-insensitive at the database level")
	void emailIsUniqueCaseInsensitively() throws SQLException {
		insertUser("Duplicate@Example.com");

		assertThatThrownBy(() -> insertUser("duplicate@example.com"))
				.isInstanceOf(SQLException.class);
	}

	private void insertUser(String email) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
					insert into app_users (id, email, email_verified, created_at, updated_at)
					values (gen_random_uuid(), '%s', false, now(), now())
					""".formatted(email));
		}
	}

	private List<String> tableNames() throws SQLException {
		List<String> names = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("""
						select table_name from information_schema.tables
						where table_schema = 'public'
						""")) {
			while (rows.next()) {
				names.add(rows.getString(1));
			}
		}
		return names;
	}

	private List<String> columnNames(String table) throws SQLException {
		List<String> names = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("""
						select column_name from information_schema.columns
						where table_schema = 'public' and table_name = '%s'
						""".formatted(table))) {
			while (rows.next()) {
				names.add(rows.getString(1));
			}
		}
		return names;
	}

	private String columnType(String table, String column) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("""
						select data_type from information_schema.columns
						where table_schema = 'public' and table_name = '%s' and column_name = '%s'
						""".formatted(table, column))) {
			assertThat(rows.next()).as("column %s.%s exists", table, column).isTrue();
			return rows.getString(1);
		}
	}
}
