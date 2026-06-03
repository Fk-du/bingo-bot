package com.bingo.app.bot.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class InputStateService {

    private final Map<Long, PendingInput> pendingActions = new ConcurrentHashMap<>();

    public void setPendingAction(Long telegramId, Action action) {
        pendingActions.put(telegramId, new PendingInput(action));
        log.debug("Set pending action for {}: {}", telegramId, action);
    }

    public void setPendingAction(Long telegramId, Action action, Map<String, Object> context) {
        pendingActions.put(telegramId, new PendingInput(action, context));
        log.debug("Set pending action with context for {}: {}", telegramId, action);
    }

    public PendingInput getPendingInput(Long telegramId) {
        return pendingActions.get(telegramId);
    }

    public void clearPendingAction(Long telegramId) {
        pendingActions.remove(telegramId);
        log.debug("Cleared pending action for {}", telegramId);
    }

    public boolean hasPendingAction(Long telegramId) {
        return pendingActions.containsKey(telegramId);
    }

    @Data
    public static class PendingInput {
        private final Action action;
        private final Map<String, Object> context;

        public PendingInput(Action action) {
            this.action = action;
            this.context = new ConcurrentHashMap<>();
        }

        public PendingInput(Action action, Map<String, Object> context) {
            this.action = action;
            this.context = context != null ? context : new ConcurrentHashMap<>();
        }
    }

    public enum Action {
        ADMIN_CREATE_GAME_ENTRY_FEE,
        FUND_PLAYER,
        FUND_AGENT,
        BUY_POINTS,
        WITHDRAW_REQUEST,
        APPROVE_WITHDRAWAL,
        REJECT_WITHDRAWAL
    }
}