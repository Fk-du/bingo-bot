package com.bingo.app.master.service;

import com.bingo.app.master.entity.PlatformConfig;
import com.bingo.app.master.repository.PlatformConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final PlatformConfigRepository configRepository;

    @Value("${bingo.max-winners:3}")
    private int defaultMaxWinners;

    @Value("${bingo.card-size:25}")
    private int defaultCardSize;

    @Value("${bingo.number-range:75}")
    private int defaultNumberRange;

    @Value("${bingo.auto-call-interval-ms:5000}")
    private int defaultAutoCallInterval;

    @Value("${app.game.default-entry-fee:10}")
    private int defaultEntryFee;

    @Value("${app.game.default-max-players:50}")
    private int defaultMaxPlayers;

    @PostConstruct
    public void seedDefaults() {
        try {
            var defaults = Map.of(
                    "maxWinners", String.valueOf(defaultMaxWinners),
                    "cardSize", String.valueOf(defaultCardSize),
                    "numberRange", String.valueOf(defaultNumberRange),
                    "autoCallInterval", String.valueOf(defaultAutoCallInterval),
                    "entryFee", String.valueOf(defaultEntryFee),
                    "maxPlayers", String.valueOf(defaultMaxPlayers)
            );

            for (var entry : defaults.entrySet()) {
                if (!configRepository.existsById(entry.getKey())) {
                    configRepository.save(new PlatformConfig(entry.getKey(), entry.getValue()));
                    log.info("Seeded default config: {}={}", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not seed default config (table may not be ready yet): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAll() {
        try {
            var entries = configRepository.findAll();
            if (entries.isEmpty()) {
                seedDefaults();
                entries = configRepository.findAll();
            }
            return entries.stream()
                    .collect(Collectors.toMap(
                            PlatformConfig::getKey,
                            c -> parseValue(c.getValue())
                    ));
        } catch (Exception e) {
            log.warn("Failed to load config from database: {}", e.getMessage());
            return getDefaultMap();
        }
    }

    @Transactional
    public void updateAll(Map<String, Object> config) {
        config.forEach((key, value) -> {
            var entity = configRepository.findById(key)
                    .orElse(new PlatformConfig(key, null));
            entity.setValue(String.valueOf(value));
            configRepository.save(entity);
        });
    }

    private Map<String, Object> getDefaultMap() {
        return Map.of(
                "maxWinners", defaultMaxWinners,
                "cardSize", defaultCardSize,
                "numberRange", defaultNumberRange,
                "autoCallInterval", defaultAutoCallInterval,
                "entryFee", defaultEntryFee,
                "maxPlayers", defaultMaxPlayers
        );
    }

    private Object parseValue(String raw) {
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
