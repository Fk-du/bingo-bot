package com.bingo.app.infrastructure.security;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramAuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserService userService;
    private final BingoTelegramBot bingoTelegramBot;

    @Value("${app.telegram.bot.token}")
    private String botToken;

    public User authenticate(String initData) {
        try {
            log.debug("Authenticating with initData: {}", initData);
            Map<String, String> params = parseInitData(initData);
            log.debug(
                    "Telegram auth payload keys={}, hashPresent={}, signaturePresent={}, botId={}, initDataLength={}",
                    params.keySet(),
                    params.containsKey("hash"),
                    params.containsKey("signature"),
                    bingoTelegramBot.getBotId(),
                    initData != null ? initData.length() : 0
            );

            if (!verifySignature(params)) {
                log.warn("Invalid Telegram signature");
                return null;
            }

            String userJson = params.get("user");
            if (userJson == null || userJson.isBlank()) {
                log.warn("Telegram auth payload is missing user data");
                return null;
            }

            JsonNode userNode = OBJECT_MAPPER.readTree(userJson);
            Long telegramId = userNode.path("id").asLong();
            String username = textOrNull(userNode, "username");
            String firstName = textOrNull(userNode, "first_name");
            String lastName = textOrNull(userNode, "last_name");

            return userService.findOrCreateUser(telegramId, username, firstName, lastName);

        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> parseInitData(String initData) {
        return Arrays.stream(initData.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        arr -> URLDecoder.decode(arr[0], StandardCharsets.UTF_8),
                        arr -> arr.length > 1 ? URLDecoder.decode(arr[1], StandardCharsets.UTF_8) : "",
                        (a, b) -> a
                ));
    }

    private boolean verifySignature(Map<String, String> params, String initData) {
        String signatureValue = params.get("signature");
        String hash = params.remove("hash");
        params.remove("signature");
        if (hash == null) {
            return verifyTelegramSignature(params, signatureValue);
        }

        String checkString = buildDataCheckString(params, null);
        String configuredBotToken = botToken == null ? "" : botToken.trim();

        if (configuredBotToken.isEmpty()) {
            return verifyTelegramSignature(params, signatureValue);
        }

        try {
            // Step 1: secret_key = HMAC-SHA256(key=bot_token, data="WebAppData")
            SecretKeySpec botTokenKey = new SecretKeySpec(
                    configuredBotToken.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            Mac innerMac = Mac.getInstance("HmacSHA256");
            innerMac.init(botTokenKey);
            byte[] secretKeyBytes = innerMac.doFinal("WebAppData".getBytes(StandardCharsets.UTF_8));

            // Step 2: signature = HMAC-SHA256(key=secret_key, data=data_check_string)
            SecretKeySpec secretKey = new SecretKeySpec(secretKeyBytes, "HmacSHA256");
            Mac outerMac = Mac.getInstance("HmacSHA256");
            outerMac.init(secretKey);
            byte[] signature = outerMac.doFinal(checkString.getBytes(StandardCharsets.UTF_8));

            String computedHash = bytesToHex(signature);
            if (computedHash.equals(hash)) {
                return true;
            }

            log.debug(
                    "Telegram HMAC hash mismatch; computedPrefix={}, receivedPrefix={}, signaturePresent={}",
                    computedHash.substring(0, Math.min(8, computedHash.length())),
                    hash.substring(0, Math.min(8, hash.length())),
                    signatureValue != null
            );
            return verifyTelegramSignature(params, signatureValue);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Signature verification failed", e);
            return verifyTelegramSignature(params, signatureValue);
        }
    }

    private boolean verifyTelegramSignature(Map<String, String> params, String signatureValue) {
        Long botId = bingoTelegramBot.getBotId();
        if (botId == null || signatureValue == null) {
            log.debug(
                    "Telegram signature fallback unavailable; botIdPresent={}, signaturePresent={}",
                    botId != null,
                    signatureValue != null
            );
            return false;
        }

        try {
            String dataCheckString = buildDataCheckString(params, botId);

            byte[] publicKey = HexFormat.of().parseHex("e7bf03a2fa4602af4580703d88dda5bb59f32ed8b02a56c187fe7d34caed242d");
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(encodeEd25519PublicKey(publicKey))));
            signature.update(dataCheckString.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureValue);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Telegram signature fallback failed", e);
            return false;
        }
    }

    private String buildDataCheckString(Map<String, String> params, Long botId) {
        String body = params.entrySet().stream()
                .filter(entry -> !"hash".equals(entry.getKey()) && !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        if (botId == null) {
            return body;
        }
        return botId + ":WebAppData\n" + body;
    }

    private byte[] encodeEd25519PublicKey(byte[] rawPublicKey) {
        // X.509 SubjectPublicKeyInfo for Ed25519:
        // SEQUENCE {
        //   SEQUENCE { OID 1.3.101.112 }
        //   BIT STRING <32-byte raw key>
        // }
        byte[] prefix = new byte[] {
                0x30, 0x2a,
                0x30, 0x05,
                0x06, 0x03, 0x2b, 0x65, 0x70,
                0x03, 0x21, 0x00
        };
        byte[] encoded = new byte[prefix.length + rawPublicKey.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(rawPublicKey, 0, encoded, prefix.length, rawPublicKey.length);
        return encoded;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }
}
