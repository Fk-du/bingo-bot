package com.bingo.app.modules.config.service;

import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.modules.config.entity.PlatformConfig;
import com.bingo.app.modules.config.repository.PlatformConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final PlatformConfigRepository repository;

    @Cacheable(value = "platformConfig", key = "#root.target.cacheKey + ':' + #key")
    public String get(String key, String defaultValue) {
        return repository.findByConfigKey(key)
                .map(PlatformConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String value = get(key, null);
        if (value == null) return defaultValue;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @CacheEvict(value = "platformConfig", allEntries = true)
    public void set(String key, String value) {
        PlatformConfig config = repository.findByConfigKey(key)
                .orElse(PlatformConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        repository.save(config);
    }

    @Cacheable(value = "platformConfig", key = "#root.target.cacheKey + ':all'")
    public Map<String, String> getAll() {
        Map<String, String> result = new HashMap<>();
        repository.findAll().forEach(c -> result.put(c.getConfigKey(), c.getConfigValue()));
        return result;
    }

    public String cacheKey() {
        String tenantId = TenantContext.get();
        return tenantId != null ? tenantId : "default";
    }
}
