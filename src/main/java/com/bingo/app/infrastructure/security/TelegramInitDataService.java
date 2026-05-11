package com.bingo.app.infrastructure.security;

import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramInitDataService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String WEB_APP_DATA = "WebAppData";

    private final UserRepository userRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${bingo.telegram.bot.token}")
    private String botToken;

    @Value("${app.super-admin.telegram-id:0}")
    private Long superAdminTelegramId;

    @Value("${bingo.telegram.auth.max-age-seconds:86400}")
    private long maxAgeSeconds;

    public Long resolveTelegramId(String authorizationHeader) {
        TelegramUserPayload payload = validateAndParse(authorizationHeader);
        return payload.telegramId();
    }

    public User resolveUser(String authorizationHeader) {
        Long telegramId = resolveTelegramId(authorizationHeader);
        return userRepository.findByTelegramId(telegramId)
                .or(() -> bootstrapSuperAdminIfNeeded(telegramId))
                .orElseThrow(() -> new TelegramAuthException(
                        "User not found for Telegram ID " + telegramId,
                        "Your Telegram account is not registered in BingoPlus yet."
                ));
    }

    private java.util.Optional<User> bootstrapSuperAdminIfNeeded(Long telegramId) {
        if (superAdminTelegramId != null && superAdminTelegramId.equals(telegramId)) {
            return java.util.Optional.of(userService.ensureSuperAdmin(telegramId));
        }

        return java.util.Optional.empty();
    }

    private TelegramUserPayload validateAndParse(String rawHeader) {
        String initData = normalizeAuthorizationHeader(rawHeader);
        Map<String, String> fields = parseQueryString(initData);

        String receivedHash = fields.get("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new TelegramAuthException("Missing Telegram initData hash", "Telegram authentication data is incomplete.");
        }

        String authDateValue = fields.get("auth_date");
        if (authDateValue == null || authDateValue.isBlank()) {
            throw new TelegramAuthException("Missing Telegram auth_date", "Telegram authentication data is incomplete.");
        }

        long authDate;
        try {
            authDate = Long.parseLong(authDateValue);
        } catch (NumberFormatException ex) {
            throw new TelegramAuthException("Invalid Telegram auth_date", "Telegram authentication data is invalid.");
        }

        long ageSeconds = Instant.now().getEpochSecond() - authDate;
        if (ageSeconds > maxAgeSeconds) {
            log.warn("Telegram initData age {} seconds exceeds configured max {} seconds. Continuing after signature verification.", ageSeconds, maxAgeSeconds);
        } else if (ageSeconds < 0) {
            log.debug("Telegram initData is {} seconds ahead of server time. Continuing after signature verification.", Math.abs(ageSeconds));
        }

        String dataCheckString = fields.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()))
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        String expectedHash = calculateHash(dataCheckString);
        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            throw new TelegramAuthException(
                    "Telegram initData hash mismatch",
                    "Telegram authentication failed. Please reload the Mini App."
            );
        }

        String userJson = fields.get("user");
        if (userJson == null || userJson.isBlank()) {
            throw new TelegramAuthException("Missing Telegram user payload", "Telegram authentication data is incomplete.");
        }

        try {
            JsonNode userNode = objectMapper.readTree(userJson);
            Long telegramId = userNode.path("id").asLong(0L);
            if (telegramId <= 0) {
                throw new TelegramAuthException("Invalid Telegram user payload", "Telegram authentication data is invalid.");
            }
            return new TelegramUserPayload(telegramId, fields);
        } catch (Exception ex) {
            throw new TelegramAuthException("Failed to parse Telegram user payload", "Telegram authentication data is invalid.");
        }
    }

    private String normalizeAuthorizationHeader(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            throw new TelegramAuthException("Missing Telegram initData", "Telegram authentication is required.");
        }

        String trimmed = rawHeader.trim();
        if (trimmed.regionMatches(true, 0, "initData ", 0, "initData ".length())) {
            return trimmed.substring("initData ".length()).trim();
        }

        return trimmed;
    }

    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            String key = decodeUrlComponent(keyValue[0]);
            String value = keyValue.length > 1 ? decodeUrlComponent(keyValue[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String decodeUrlComponent(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String calculateHash(String dataCheckString) {
        try {
            Mac firstMac = Mac.getInstance(HMAC_ALGORITHM);
            firstMac.init(new SecretKeySpec(WEB_APP_DATA.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] secretKey = firstMac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            Mac secondMac = Mac.getInstance(HMAC_ALGORITHM);
            secondMac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            byte[] digest = secondMac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new TelegramAuthException("Unable to verify Telegram initData", "Telegram authentication failed.");
        }
    }

    private record TelegramUserPayload(Long telegramId, Map<String, String> fields) {
    }
}
