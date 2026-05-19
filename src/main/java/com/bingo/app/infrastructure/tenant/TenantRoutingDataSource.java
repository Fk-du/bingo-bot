package com.bingo.app.infrastructure.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final Map<Object, Object> tenantDataSources = new ConcurrentHashMap<>();

    private static final String MASTER_TENANT = "master";

    public TenantRoutingDataSource(String baseUrl, String username, String password,
                                   String defaultDatabase) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;

        String masterDatabaseUrl = baseUrl.replaceAll("/[^/]+$", "/" + defaultDatabase);

        HikariDataSource defaultDs = createDataSource(masterDatabaseUrl);
        tenantDataSources.put(MASTER_TENANT, defaultDs);
        tenantDataSources.put("master", defaultDs);

        setDefaultTargetDataSource(defaultDs);
        setTargetDataSources(tenantDataSources);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            return MASTER_TENANT;
        }

        if (!tenantDataSources.containsKey(tenantId)) {
            createTenantDataSourceIfNeeded(tenantId);
        }

        return tenantId;
    }

    public synchronized void addTenant(String tenantId, String databaseName) {
        if (tenantDataSources.containsKey(tenantId)) {
            return;
        }
        String dbUrl = baseUrl.replaceAll("/[^/]+$", "/" + databaseName);
        HikariDataSource ds = createDataSource(dbUrl);
        tenantDataSources.put(tenantId, ds);
        setTargetDataSources(tenantDataSources);
        afterPropertiesSet();
    }

    private synchronized void createTenantDataSourceIfNeeded(String tenantId) {
        if (tenantDataSources.containsKey(tenantId)) {
            return;
        }
        String databaseName = "bingo_" + tenantId;
        String dbUrl = baseUrl.replaceAll("/[^/]+$", "/" + databaseName);
        HikariDataSource ds = createDataSource(dbUrl);
        tenantDataSources.put(tenantId, ds);
        setTargetDataSources(tenantDataSources);
        afterPropertiesSet();
    }

    private HikariDataSource createDataSource(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setIdleTimeout(300000);
        ds.setConnectionTimeout(30000);
        return ds;
    }
}
