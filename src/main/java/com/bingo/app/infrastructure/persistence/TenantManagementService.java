package com.bingo.app.infrastructure.persistence;

import com.bingo.app.master.entity.TenantRegistry;
import com.bingo.app.master.repository.TenantRegistryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantManagementService {

    private static final List<String> TENANT_SQL_SCRIPTS = List.of(
            "db/migration/V1__initial_tenant_schema.sql",
            "db/migration/V2__add_player_balance.sql",
            "db/migration/V3__add_game_card_banned.sql",
            "db/migration/V4__add_withdrawal_rejection_reason.sql",
            "db/migration/V5__add_bingo_claim_validated_by.sql",
            "db/migration/V6__add_bingo_claim_rejection_reason.sql",
            "db/migration/V7__add_game_commission_percent.sql",
            "db/migration/V8__add_game_fairness_hash.sql",
            "db/migration/V9__add_game_auto_mark.sql",
            "db/migration/V10__add_game_card_marked_numbers.sql",
            "db/migration/V11__add_race_condition_constraints.sql",
            "db/migration/V12__add_game_custom_pattern.sql",
            "db/migration/V13__add_game_card_auto_mark.sql"
    );

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
        runMasterMigrations();
        migrateExistingTenants();
    }

    public TenantRegistry createTenant(Long adminUserId) {
        String databaseName = "bingo_agent_" + adminUserId;

        createDatabase(databaseName);
        initializeTenantSchema(databaseName);

        TenantRegistry registry = tenantRegistryRepository.findByAdminUserId(adminUserId)
                .orElseGet(() -> TenantRegistry.builder()
                        .adminUserId(adminUserId)
                        .databaseName(databaseName)
                        .build());
        registry.setDatabaseName(databaseName);
        TenantRegistry saved = tenantRegistryRepository.save(registry);

        routingDataSource.addTenant("agent_" + adminUserId, databaseName);

        log.info("Tenant ensured: {} for admin user {}", databaseName, adminUserId);
        return saved;
    }

    private void migrateExistingTenants() {
        List<TenantRegistry> registries;
        try {
            registries = tenantRegistryRepository.findAll();
        } catch (Exception e) {
            log.warn("Could not load tenant registry via JPA: {}", e.getMessage());
            return;
        }

        for (TenantRegistry registry : registries) {
            try {
                createDatabase(registry.getDatabaseName());
                initializeTenantSchema(registry.getDatabaseName());
                routingDataSource.addTenant(
                        "agent_" + registry.getAdminUserId(),
                        registry.getDatabaseName());
            } catch (Exception e) {
                log.error("Failed to migrate tenant database {}: {}", registry.getDatabaseName(), e.getMessage(), e);
            }
        }
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
            String tenantUrl = tenantBaseUrl.replaceAll("/\\w+$", "/" + databaseName);

            log.info("Applying tenant schema migrations to: {}", tenantUrl);
            try (Connection conn = DriverManager.getConnection(tenantUrl, tenantUsername, tenantPassword)) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE SCHEMA IF NOT EXISTS public");
                }
                for (String script : TENANT_SQL_SCRIPTS) {
                    try {
                        ScriptUtils.executeSqlScript(conn, new ClassPathResource(script));
                    } catch (Exception ex) {
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        if (msg != null && (msg.contains("already exists") || msg.contains("duplicate"))) {
                            log.info("Migration {} already applied to {}: {}", script, databaseName, msg);
                        } else {
                            throw ex;
                        }
                    }
                }
                SchemaMigrationHelper.runTenantMigrations(conn);
            }
            log.info("Tenant schema up to date: {}", databaseName);
        } catch (Exception e) {
            log.error("Schema migration failed for database: {}", databaseName, e);
            throw new RuntimeException("Failed to migrate tenant schema: " + e.getMessage(), e);
        }
    }

    private void runMasterMigrations() {
        try (Connection conn = masterDataSource.getConnection()) {
            conn.setAutoCommit(true);
            SchemaMigrationHelper.runMasterMigrations(conn);
            log.info("Master schema migrations applied");
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply master schema migrations: " + e.getMessage(), e);
        }
    }

    public void ensureMasterSchema() {
        try (Connection conn = masterDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS public");

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS deposit_account_info TEXT");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    // column already present
                } else {
                    throw e;
                }
            }

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tenant_registry (
                    id BIGSERIAL PRIMARY KEY,
                    admin_user_id BIGINT UNIQUE NOT NULL,
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
                    admin_user_id BIGINT,
                    parent_id BIGINT,
                    business_name VARCHAR(255),
                    admin_approved BOOLEAN NOT NULL DEFAULT FALSE,
                    balance DECIMAL(19,2) DEFAULT 0,
                    frozen_balance DECIMAL(19,2) DEFAULT 0,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
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
                CREATE TABLE IF NOT EXISTS admin_fund_requests (
                    id BIGSERIAL PRIMARY KEY,
                    admin_user_id BIGINT NOT NULL,
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
            throw new RuntimeException("Failed to ensure master schema: " + e.getMessage(), e);
        }
    }
}
