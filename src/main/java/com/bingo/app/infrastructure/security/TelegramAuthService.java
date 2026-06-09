package com.bingo.app.infrastructure.security;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthService {

    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.telegram.bot.token}")
    private String botToken;

    public User authenticate(String initData) {
        try {
            log.debug("Authenticating with initData: {}", initData);
            Map<String, String> params = parseInitData(initData);
            log.debug("Parsed params keys: {}", params.keySet());

            if (!verifySignature(params)) {
                log.warn("Invalid Telegram signature");
                return null;
            }

            String userJson = params.get("user");
            if (userJson == null) {
                log.warn("No user data in initData");
                return null;
            }

            JsonNode userNode = objectMapper.readTree(userJson);
            Long telegramId = userNode.get("id").asLong();
            String username = userNode.has("username") ? userNode.get("username").asText(null) : null;
            String firstName = userNode.has("first_name") ? userNode.get("first_name").asText(null) : null;
            String lastName = userNode.has("last_name") ? userNode.get("last_name").asText(null) : null;

            return userService.findOrCreateUser(telegramId, username, firstName, lastName);

        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> params = new HashMap<>();
        for (String part : initData.split("&")) {
            String[] kv = part.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private boolean verifySignature(Map<String, String> params) {
        String hash = params.get("hash");
        if (hash == null) {
            log.warn("No hash in initData");
            return false;
        }

        // Build check string from URL-decoded params (sorted, excluding hash)
        String checkString = params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("hash"))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        log.debug("Check string: {}", checkString);

        try {
            // Step 1: derive secret key: HMAC-SHA256(key="WebAppData", message=bot_token)
            Mac innerMac = Mac.getInstance("HmacSHA256");
            innerMac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKeyBytes = innerMac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            // Step 2: compute signature: HMAC-SHA256(key=secretKey, message=checkString)
            Mac outerMac = Mac.getInstance("HmacSHA256");
            outerMac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));

            String computedHash = bytesToHex(outerMac.doFinal(checkString.getBytes(StandardCharsets.UTF_8)));
            log.debug("Expected hash: {}", hash);
            log.debug("Computed hash: {}", computedHash);
            return computedHash.equals(hash);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}