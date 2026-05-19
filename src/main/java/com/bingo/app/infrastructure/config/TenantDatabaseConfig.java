package com.bingo.app.infrastructure.config;

import com.bingo.app.infrastructure.tenant.TenantRoutingDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.bingo.app.modules.game.repository",
                "com.bingo.app.modules.wallet.repository",
                "com.bingo.app.modules.audit.repository",
                "com.bingo.app.modules.topup.repository",
                "com.bingo.app.modules.config.repository"
        },
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager"
)
public class TenantDatabaseConfig {

    @Value("${tenant.datasource.url}")
    private String tenantBaseUrl;

    @Value("${tenant.datasource.username}")
    private String tenantUsername;

    @Value("${tenant.datasource.password}")
    private String tenantPassword;

    @Value("${tenant.datasource.default-database:bingo_master}")
    private String defaultDatabase;

    @Value("${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.PostgreSQLDialect}")
    private String hibernateDialect;

    @Value("${tenant.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @Bean(name = "tenantRoutingDataSource")
    public TenantRoutingDataSource tenantRoutingDataSource() {
        return new TenantRoutingDataSource(tenantBaseUrl, tenantUsername, tenantPassword, defaultDatabase);
    }

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            @Qualifier("tenantRoutingDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan(
                "com.bingo.app.modules.game.entity",
                "com.bingo.app.modules.wallet.entity",
                "com.bingo.app.modules.audit.entity",
                "com.bingo.app.modules.topup.entity",
                "com.bingo.app.modules.config.entity"
        );
        emf.setPersistenceUnitName("tenant");

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);

        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", ddlAuto);
        props.setProperty("hibernate.dialect", hibernateDialect);
        emf.setJpaProperties(props);

        return emf;
    }

    @Bean(name = "tenantTransactionManager")
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
