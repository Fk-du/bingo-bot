package com.bingo.app.infrastructure.security;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthService {

    private final UserService userService;

    @Value("${app.telegram.bot.token}")
    private String botToken;

    public User authenticate(String initData) {
        try {
            Map<String, String> params = parseInitData(initData);

            if (!verifySignature(params, initData)) {
                log.warn("Invalid Telegram signature");
                return null;
            }

            Long telegramId = Long.parseLong(params.get("id"));
            String username = params.get("username");
            String firstName = params.get("first_name");
            String lastName = params.get("last_name");

            return userService.findOrCreateUser(telegramId, username, firstName, lastName);

        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseInitData(String initData) {
        return Arrays.stream(initData.split("&"))
                .map(part -> part.split("="))
                .collect(Collectors.toMap(
                        arr -> arr[0],
                        arr -> arr.length > 1 ? arr[1] : "",
                        (a, b) -> a
                ));
    }

    private boolean verifySignature(Map<String, String> params, String initData) {
        String hash = params.remove("hash");
        if (hash == null) return false;

        String checkString = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                    ("WebAppData" + botToken).getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(checkString.getBytes(StandardCharsets.UTF_8));

            String computedHash = bytesToHex(signature);
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