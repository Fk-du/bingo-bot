package com.bingo.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserRegistration(
        long telegramId,
        BigDecimal balance
) {
}
