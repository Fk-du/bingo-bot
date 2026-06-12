package com.bingo.app.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public final class SchemaMigrationHelper {

    private SchemaMigrationHelper() {
    }

    public static void runMasterMigrations(Connection conn) throws SQLException {
        renameColumnIfExists(conn, "users", "agent_id", "admin_user_id");
        renameColumnIfExists(conn, "tenant_registry", "agent_id", "admin_user_id");

        addColumnIfNotExists(conn, "users", "business_name", "VARCHAR(255)");
        addColumnIfNotExists(conn, "users", "admin_approved", "BOOLEAN NOT NULL DEFAULT FALSE");
        ensureUserColumnBoolean(conn, "users", "admin_approved");
        addColumnIfNotExists(conn, "users", "active", "BOOLEAN NOT NULL DEFAULT TRUE");
        ensureUserColumnBoolean(conn, "users", "active");

        migrateAgentsIntoUsers(conn);

        if (tableExists(conn, "agent_fund_requests")) {
            renameColumnIfExists(conn, "agent_fund_requests", "agent_id", "admin_user_id");
            if (!tableExists(conn, "admin_fund_requests")) {
                execute(conn, "ALTER TABLE agent_fund_requests RENAME TO admin_fund_requests");
                log.info("Renamed table agent_fund_requests -> admin_fund_requests");
            }
        }

        dropIndexIfExists(conn, "idx_users_agent");
        createIndexIfNotExists(conn, "idx_users_admin", "users", "admin_user_id");

        ensureTenantRegistryDatabaseName(conn);

        dropIndexIfExists(conn, "idx_tenant_registry_agent");
        createIndexIfNotExists(conn, "idx_tenant_registry_admin", "tenant_registry", "admin_user_id");

        dropIndexIfExists(conn, "idx_agent_fund_agent");
        dropIndexIfExists(conn, "idx_agent_fund_status");
        if (tableExists(conn, "admin_fund_requests")) {
            createIndexIfNotExists(conn, "idx_admin_fund_admin", "admin_fund_requests", "admin_user_id");
            createIndexIfNotExists(conn, "idx_admin_fund_status", "admin_fund_requests", "status");
        }
    }

    public static void runTenantMigrations(Connection conn) throws SQLException {
        renameColumnIfExists(conn, "players", "agent_id", "admin_user_id");
        renameColumnIfExists(conn, "games", "agent_id", "admin_user_id");

        dropIndexIfExists(conn, "idx_players_agent");
        createIndexIfNotExists(conn, "idx_players_admin", "players", "admin_user_id");

        dropIndexIfExists(conn, "idx_games_agent_status");
        createIndexIfNotExists(conn, "idx_games_admin_status", "games", "admin_user_id, status");
    }

    private static void migrateAgentsIntoUsers(Connection conn) throws SQLException {
        if (!tableExists(conn, "agents")) {
            return;
        }

        execute(conn, """
                UPDATE users u
                SET business_name = COALESCE(u.business_name, ap.business_name),
                    admin_approved = CASE WHEN ap.approved THEN TRUE ELSE COALESCE(u.admin_approved, FALSE) END
                FROM agents ap
                WHERE ap.user_id = u.id
                """);

        execute(conn, "DROP TABLE agents");
        log.info("Migrated agents table into users and dropped agents");
    }

    static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (var ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static void renameColumnIfExists(Connection conn, String table, String from, String to) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        if (columnExists(conn, table, from) && !columnExists(conn, table, to)) {
            execute(conn, "ALTER TABLE " + table + " RENAME COLUMN " + from + " TO " + to);
            log.info("Renamed column {}.{} -> {}", table, from, to);
        }
    }

    private static void ensureTenantRegistryDatabaseName(Connection conn) throws SQLException {
        if (!tableExists(conn, "tenant_registry")) {
            return;
        }

        addColumnIfNotExists(conn, "tenant_registry", "database_name", "VARCHAR(255)");

        // Backfill null database_name using admin_user_id (fallback to id if admin_user_id is null)
        execute(conn, """
                UPDATE tenant_registry
                SET database_name = 'bingo_agent_' || COALESCE(admin_user_id::text, id::text)
                WHERE database_name IS NULL
                """);

        // Ensure NOT NULL constraint only after verifying no nulls remain
        execute(conn, """
                ALTER TABLE tenant_registry
                ALTER COLUMN database_name SET NOT NULL
                """);

        // Add unique constraint if missing
        execute(conn, """
                DO $$ BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint
                        WHERE conname = 'uk_tenant_registry_database_name'
                        AND conrelid = 'tenant_registry'::regclass
                    ) THEN
                        ALTER TABLE tenant_registry
                        ADD CONSTRAINT uk_tenant_registry_database_name UNIQUE (database_name);
                    END IF;
                END $$;
                """);

        log.info("Ensured database_name column on tenant_registry");
    }

    private static void ensureUserColumnBoolean(Connection conn, String table, String column) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        // Backfill null values with false
        execute(conn, "UPDATE " + table + " SET " + column + " = FALSE WHERE " + column + " IS NULL");
        log.info("Backfilled null {}.{} with FALSE", table, column);
        // Ensure NOT NULL
        execute(conn, "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NOT NULL");
        log.info("Ensured NOT NULL on {}.{}", table, column);
    }

    static void addColumnIfNotExists(Connection conn, String table, String column, String definition) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        if (!columnExists(conn, table, column)) {
            execute(conn, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Added column {}.{}", table, column);
        }
    }

    static void createIndexIfNotExists(Connection conn, String indexName, String table, String columns) throws SQLException {
        if (!tableExists(conn, table)) {
            return;
        }
        execute(conn, "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + "(" + columns + ")");
    }

    static void dropIndexIfExists(Connection conn, String indexName) throws SQLException {
        execute(conn, "DROP INDEX IF EXISTS " + indexName);
    }

    static void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
