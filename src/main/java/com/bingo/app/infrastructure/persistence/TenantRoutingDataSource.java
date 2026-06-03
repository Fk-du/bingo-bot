package com.bingo.app.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
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

    public TenantRoutingDataSource(String baseUrl, String username, String password, String masterDatabase) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;

        // Extract base URL without database name
        String baseDbUrl = baseUrl.replaceAll("/[^/]+$", "/");
        String masterUrl = baseDbUrl + masterDatabase;

        HikariDataSource masterDs = createDataSource(masterUrl);
        tenantDataSources.put("master", masterDs);

        setDefaultTargetDataSource(masterDs);
        setTargetDataSources(tenantDataSources);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenant();
    }

    public void addTenant(String tenantId, String databaseName) {
        if (!tenantDataSources.containsKey(tenantId)) {
            String baseDbUrl = baseUrl.replaceAll("/[^/]+$", "/");
            String dbUrl = baseDbUrl + databaseName;
            HikariDataSource ds = createDataSource(dbUrl);
            tenantDataSources.put(tenantId, ds);
            setTargetDataSources(tenantDataSources);
            afterPropertiesSet();
        }
    }

    private HikariDataSource createDataSource(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("tenant-pool-" + System.currentTimeMillis());

        return new HikariDataSource(config);
    }
}