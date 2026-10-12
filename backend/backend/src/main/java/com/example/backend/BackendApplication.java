package com.example.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class BackendApplication {

	private static final Logger logger = LoggerFactory.getLogger(BackendApplication.class);

	@Bean
	ApplicationRunner logConnectedDatabase(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				DatabaseDiagnostic diagnostic = jdbcTemplate.queryForObject(
						"SELECT DATABASE(), @@hostname, COUNT(*) FROM items",
						(rs, rowNum) -> new DatabaseDiagnostic(
								rs.getString(1),
								rs.getString(2),
								rs.getLong(3)
						)
				);
				logger.info("Database diagnostic: database={}, hostname={}, itemCount={}",
						diagnostic.database(), diagnostic.hostname(), diagnostic.itemCount());
			} catch (DataAccessException exception) {
				logger.warn("Database diagnostic failed; application startup will continue.", exception);
			}
		};
	}

	private record DatabaseDiagnostic(String database, String hostname, long itemCount) {}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
