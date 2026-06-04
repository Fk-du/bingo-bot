package com.bingo.app.infrastructure.tenant;

import com.bingo.app.infrastructure.entity.TenantRegistry;
import com.bingo.app.infrastructure.repository.TenantRegistryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TenantManagementService {

    private final TenantRegistryRepository tenantRegistryRepository;
    private final DataSource masterDataSource;
    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TransactionTemplate masterTransactionTemplate;

    public TenantManagementService(TenantRegistryRepository tenantRegistryRepository,
                                   @Qualifier("masterDataSource") DataSource masterDataSource,
                                   TenantRoutingDataSource tenantRoutingDataSource,
                                   @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager) {
        this.tenantRegistryRepository = tenantRegistryRepository;
        this.masterDataSource = masterDataSource;
        this.tenantRoutingDataSource = tenantRoutingDataSource;
        this.masterTransactionTemplate = new TransactionTemplate(masterTransactionManager);
        this.masterTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Value("${tenant.datasource.default-database:bingo_master}")
    private String defaultDatabase;

    public TenantRegistry registerTenant(Long adminId) {
        if (tenantRegistryRepository.existsByAdminId(adminId)) {
            return tenantRegistryRepository.findByAdminId(adminId).orElseThrow();
        }

        String databaseName = "bingo_agent_" + adminId;

        createDatabaseIfNotExists(databaseName);

        TenantRegistry saved = masterTransactionTemplate.execute(status -> {
            TenantRegistry registry = TenantRegistry.builder()
                    .adminId(adminId)
                    .databaseName(databaseName)
                    .createdAt(LocalDateTime.now())
                    .build();
            return tenantRegistryRepository.save(registry);
        });

        String tenantId = TenantContext.agentTenant(adminId);
        tenantRoutingDataSource.addTenant(tenantId, databaseName);

        initializeTenantSchema(tenantId, databaseName);

        log.info("Tenant database '{}' registered for adminId={}", databaseName, adminId);
        return saved;
    }

    private void createDatabaseIfNotExists(String databaseName) {
        if (!databaseName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid database name: " + databaseName);
        }

        try (Connection conn = masterDataSource.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(
                     "SELECT 1 FROM pg_database WHERE datname = ?")) {

            checkStmt.setString(1, databaseName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    log.info("Database '{}' already exists, skipping creation.", databaseName);
                    return;
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE \"" + databaseName + "\"");
            }
            log.info("Created tenant database '{}'", databaseName);
        } catch (Exception e) {
            log.warn("Could not create database '{}': {}. It may already exist or require superuser privileges.",
                    databaseName, e.getMessage());
        }
    }

    public void ensureMasterSchema() {
        try (Connection conn = masterDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            log.info("Connected to database: {}", conn.getMetaData().getURL());

            stmt.execute("SET search_path TO public");

            try {
                stmt.execute("GRANT ALL ON SCHEMA public TO PUBLIC");
            } catch (Exception e) {
                log.debug("Could not grant schema permissions (may not be superuser): {}", e.getMessage());
            }

            try (var rs = stmt.executeQuery(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'tenant_registry' AND table_schema = 'public')")) {
                if (rs.next() && rs.getBoolean(1)) {
                    log.info("Master schema already exists.");
                    return;
                }
            }

            String[] masterTables = {
                "CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "telegramId BIGINT UNIQUE NOT NULL, " +
                    "role VARCHAR(20) NOT NULL DEFAULT 'PLAYER', " +
                    "parentId BIGINT REFERENCES users(id), " +
                    "balance DECIMAL(19,2) NOT NULL DEFAULT 0, " +
                    "frozenBalance DECIMAL(19,2) NOT NULL DEFAULT 0, " +
                    "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                    "createdAt TIMESTAMP NOT NULL DEFAULT NOW())",

                "CREATE TABLE IF NOT EXISTS tenant_registry (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "adminId BIGINT UNIQUE NOT NULL, " +
                    "databaseName VARCHAR(255) NOT NULL, " +
                    "createdAt TIMESTAMP NOT NULL DEFAULT NOW())",

                "CREATE TABLE IF NOT EXISTS invite_codes (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "code VARCHAR(50) UNIQUE NOT NULL, " +
                    "adminId BIGINT NOT NULL, " +
                    "active BOOLEAN NOT NULL DEFAULT TRUE, " +
                    "createdAt TIMESTAMP NOT NULL DEFAULT NOW())",

                "CREATE INDEX IF NOT EXISTS idx_users_telegram_id ON users(telegramId)",
                "CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)",
                "CREATE INDEX IF NOT EXISTS idx_users_parent_id ON users(parentId)",
                "CREATE INDEX IF NOT EXISTS idx_tenant_registry_admin_id ON tenant_registry(adminId)",
                "CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code)"
            };

            for (String ddl : masterTables) {
                log.debug("Executing: {}", ddl.substring(0, Math.min(100, ddl.length())));
                stmt.executeUpdate(ddl);
            }

            try (var rs = stmt.executeQuery(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'tenant_registry' AND table_schema = 'public')")) {
                if (rs.next() && rs.getBoolean(1)) {
                    log.info("Master schema created successfully.");
                } else {
                    log.error("Failed to create master schema - tenant_registry table still missing after creation attempt!");
                    throw new RuntimeException("Could not create master schema tables. Check PostgreSQL permissions.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to ensure master schema: {}", e.getMessage(), e);
            throw new RuntimeException("Could not ensure master schema. Reason: " + e.getMessage(), e);
        }
    }

    public void initializeTenants() {
        java.util.List<TenantRegistry> tenants = tenantRegistryRepository.findAll();
        for (TenantRegistry tenant : tenants) {
            String tenantId = TenantContext.agentTenant(tenant.getAdminId());
            tenantRoutingDataSource.addTenant(tenantId, tenant.getDatabaseName());
            initializeTenantSchema(tenantId, tenant.getDatabaseName());
            log.info("Loaded tenant '{}' with database '{}'", tenantId, tenant.getDatabaseName());
        }
        log.info("Loaded {} tenant(s) from registry.", tenants.size());
    }

    public String getTenantIdForAdmin(Long adminId) {
        return TenantContext.agentTenant(adminId);
    }

    private void initializeTenantSchema(String tenantId, String databaseName) {
        try {
            var resource = new ClassPathResource("db/migration/V1__tenant_schema.sql");
            String sql;
            try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }

            TenantContext.set(tenantId);
            try (Connection conn = tenantRoutingDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                conn.setCatalog(databaseName);
                
                // Set search_path to public schema before creating tables
                stmt.execute("SET search_path TO public");

                String[] statements = sql.split(";");
                for (String st : statements) {
                    String trimmed = st.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        stmt.executeUpdate(trimmed);
                    }
                }
            }
            log.info("Schema initialized for tenant database '{}'", databaseName);
        } catch (Exception e) {
            log.warn("Could not initialize schema for tenant '{}': {}", databaseName, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
