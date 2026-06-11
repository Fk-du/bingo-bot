package com.bingo.app.infrastructure.security;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class TelegramAuthServiceTest {

    @Test
    void authenticate_acceptsInitDataWhenSignatureFieldIsPresent() {
        UserService userService = Mockito.mock(UserService.class);
        BingoTelegramBot bingoTelegramBot = Mockito.mock(BingoTelegramBot.class);
        TelegramAuthService service = new TelegramAuthService(userService, bingoTelegramBot);
        ReflectionTestUtils.setField(service, "botToken", "test-bot-token");

        User expectedUser = User.builder()
                .telegramId(123L)
                .username("alice")
                .firstName("Alice")
                .lastName("Smith")
                .role(Role.PLAYER)
                .active(true)
                .build();

        when(userService.findOrCreateUser(eq(123L), eq("alice"), eq("Alice"), eq("Smith")))
                .thenReturn(expectedUser);

        String initData = buildInitData(
                Map.of(
                        "auth_date", "1710000000",
                        "query_id", "AAEAAAE",
                        "user", "{\"id\":123,\"first_name\":\"Alice\",\"last_name\":\"Smith\",\"username\":\"alice\"}"
                ),
                "signed-by-telegram"
        );

        User actual = service.authenticate(initData);

        assertNotNull(actual);
        assertSame(expectedUser, actual);
    }

    @Test
    void authenticate_rejectsPayloadWithInvalidHash() {
        UserService userService = Mockito.mock(UserService.class);
        BingoTelegramBot bingoTelegramBot = Mockito.mock(BingoTelegramBot.class);
        TelegramAuthService service = new TelegramAuthService(userService, bingoTelegramBot);
        ReflectionTestUtils.setField(service, "botToken", "test-bot-token");

        String initData = "user=%7B%22id%22%3A123%2C%22username%22%3A%22alice%22%7D&auth_date=1710000000&hash=deadbeef";

        assertNull(service.authenticate(initData));
    }

    private static String buildInitData(Map<String, String> fields, String signature) {
        Map<String, String> orderedFields = new LinkedHashMap<>(fields);
        String checkString = orderedFields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        String hash = hmacHex("test-bot-token", "WebAppData", checkString);

        return orderedFields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"))
                + "&hash=" + hash
                + "&signature=" + signature;
    }

    private static String hmacHex(String key, String data, String message) {
        try {
            Mac innerMac = Mac.getInstance("HmacSHA256");
            innerMac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKeyBytes = innerMac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            Mac outerMac = Mac.getInstance("HmacSHA256");
            outerMac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            byte[] signatureBytes = outerMac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : signatureBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate test HMAC", e);
        }
    }
}
