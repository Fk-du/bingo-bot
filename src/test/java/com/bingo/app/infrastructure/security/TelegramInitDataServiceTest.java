package com.bingo.app.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.repository.UserRepository;
import com.bingo.app.modules.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class TelegramInitDataServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private TelegramInitDataService telegramInitDataService;

    @BeforeEach
    void setUp() {
        telegramInitDataService = new TelegramInitDataService(userRepository, userService, new ObjectMapper());
        ReflectionTestUtils.setField(telegramInitDataService, "botToken", "test-bot-token");
        ReflectionTestUtils.setField(telegramInitDataService, "maxAgeSeconds", 1L);
    }

    @Test
    void resolveUserAcceptsExpiredInitDataWhenSignatureIsValid() {
        User user = User.builder()
                .id(1L)
                .telegramId(12345L)
                .role(Role.PLAYER)
                .active(true)
                .build();

        when(userRepository.findByTelegramId(12345L)).thenReturn(Optional.of(user));

        long authDate = Instant.now().minusSeconds(3600).getEpochSecond();
        String userJson = "{\"id\":12345,\"first_name\":\"Test\"}";
        String dataCheckString = "auth_date=" + authDate + "\nuser=" + userJson;
        String hash = hmacHex(dataCheckString, "test-bot-token");
        String initData = "auth_date=" + authDate + "&user=" + urlEncode(userJson) + "&hash=" + hash;

        User resolved = assertDoesNotThrow(() -> telegramInitDataService.resolveUser(initData));

        assertEquals(user, resolved);
    }

    private String hmacHex(String dataCheckString, String botToken) {
        try {
            Mac firstMac = Mac.getInstance("HmacSHA256");
            firstMac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = firstMac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            Mac secondMac = Mac.getInstance("HmacSHA256");
            secondMac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] digest = secondMac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
