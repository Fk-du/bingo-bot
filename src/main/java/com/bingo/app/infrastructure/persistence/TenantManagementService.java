package com.bingo.app.infrastructure.persistence;

import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.repository.TenantRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantManagementService {

    private final TenantRegistryRepository tenantRegistryRepository;
    private final DataSource masterDataSource;
    private final TenantRoutingDataSource routingDataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${tenant.datasource.url}")
    private String tenantBaseUrl;

    @Value("${tenant.datasource.username}")
    private String tenantUsername;

    @Value("${tenant.datasource.password}")
    private String tenantPassword;

    @PostConstruct
    public void init() {
        log.info("Ensuring master schema exists...");
        ensureMasterSchema();
    }

    // NOT @Transactional — CREATE DATABASE cannot run inside a transaction block.
    // Individual JpaRepository calls handle their own implicit transactions.
    // Fully idempotent: safe to call multiple times for the same agent.
    public TenantRegistry createTenant(Long agentId) {
        String databaseName = "bingo_agent_" + agentId;

        // Always create the database (safe with IF NOT EXISTS equivalent in the catch)
        createDatabase(databaseName);
        // Always re-initialize the schema (all statements use CREATE IF NOT EXISTS)
        initializeTenantSchema(databaseName);

        // Upsert tenant registry entry
        TenantRegistry registry = tenantRegistryRepository.findByAgentId(agentId)
                .orElseGet(() -> TenantRegistry.builder()
                        .agentId(agentId)
                        .databaseName(databaseName)
                        .build());
        registry.setDatabaseName(databaseName);
        TenantRegistry saved = tenantRegistryRepository.save(registry);

        // Ensure routing datasource has this tenant
        routingDataSource.addTenant("agent_" + agentId, databaseName);

        log.info("Tenant ensured: {} for agent {}", databaseName, agentId);
        return saved;
    }

    private void createDatabase(String databaseName) {
        try {
            jdbcTemplate.execute("CREATE DATABASE \"" + databaseName + "\"");
            log.info("Database created: {}", databaseName);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                msg = e.getCause().getMessage();
            }
            if (msg.contains("already exists")) {
                log.info("Database already exists: {}", databaseName);
            } else {
                log.error("Failed to create database: {}", databaseName, e);
                throw new RuntimeException("Failed to create database: " + databaseName, e);
            }
        }
    }

    private void initializeTenantSchema(String databaseName) {
        try {
            String schemaSql = loadSchemaSql();

            // Build tenant JDBC URL by replacing the database name in the base URL
            String tenantUrl = tenantBaseUrl.replaceAll("/\\w+$", "/" + databaseName);

            log.info("Connecting to tenant database: {}", tenantUrl);
            try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUsername, tenantPassword)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS public");
                    for (String sql : schemaSql.split(";")) {
                        String trimmed = sql.trim();
                        if (!trimmed.isEmpty()) {
                            trimmed = trimmed.replaceAll("(?m)^--.*$", "").trim();
                            if (!trimmed.isEmpty()) {
                                log.debug("Executing SQL in {}: {}", databaseName, trimmed.substring(0, Math.min(80, trimmed.length())));
                                stmt.execute(trimmed);
                            }
                        }
                    }
                }
            }
            log.info("Schema initialized for: {}", databaseName);
        } catch (Exception e) {
            log.error("Schema initialization failed for database: {}", databaseName, e);
            throw new RuntimeException("Failed to initialize tenant schema: " + e.getMessage(), e);
        }
    }

    private String loadSchemaSql() throws Exception {
        var resource = new ClassPathResource("db/migration/V1__initial_tenant_schema.sql");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public void ensureMasterSchema() {
        try (Connection conn = masterDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS public");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tenant_registry (
                    id BIGSERIAL PRIMARY KEY,
                    agent_id BIGINT UNIQUE NOT NULL,
                    database_name VARCHAR(255) UNIQUE NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    telegram_id BIGINT UNIQUE NOT NULL,
                    username VARCHAR(100),
                    first_name VARCHAR(100),
                    last_name VARCHAR(100),
                    role VARCHAR(20) NOT NULL,
                    agent_id BIGINT,
                    parent_id BIGINT,
                    balance DECIMAL(19,2) DEFAULT 0,
                    frozen_balance DECIMAL(19,2) DEFAULT 0,
                    active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS invite_codes (
                    id BIGSERIAL PRIMARY KEY,
                    code VARCHAR(50) UNIQUE NOT NULL,
                    creator_id BIGINT NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agents (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT UNIQUE NOT NULL,
                    business_name VARCHAR(255),
                    approved BOOLEAN DEFAULT FALSE,
                    active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS agent_fund_requests (
                    id BIGSERIAL PRIMARY KEY,
                    agent_id BIGINT NOT NULL,
                    amount DECIMAL(19,2) NOT NULL,
                    screenshot_url VARCHAR(500),
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    approved_by BIGINT,
                    approved_at TIMESTAMP,
                    rejection_reason TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);

            log.info("Master schema ensured");
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure master schema", e);
        }
    }
}