package com.bingo.app.master.dto.request;

import java.math.BigDecimal;
import java.util.Map;

public record ConfigUpdateRequest(
        Map<String, Object> config
) {}
