package com.bingo.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires bingo_master PostgreSQL database to exist. Run scripts/setup-databases.sql first.")
class BingoApplicationTests {

	@Test
	void contextLoads() {
	}
}
