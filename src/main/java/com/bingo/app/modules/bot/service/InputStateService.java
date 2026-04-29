package com.bingo.app.modules.bot.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InputStateService {

    public enum Action {
        ADMIN_CREATE_GAME_ENTRY_FEE,
        FUND_AGENT,
        FUND_PLAYER,
        BUY_POINTS,
        WITHDRAW_REQUEST,
        BUY_POINTS_UPLOAD_PROOF,
        WITHDRAW_REQUEST_UPLOAD_PROOF,
        ADMIN_WITHDRAW_REVIEW
    }

    public static class PendingInput {
        private final Action action;
        private final java.math.BigDecimal amount;

        public PendingInput(Action action, java.math.BigDecimal amount) {
            this.action = action;
            this.amount = amount;
        }

        public Action getAction() {
            return action;
        }

        public java.math.BigDecimal getAmount() {
            return amount;
        }
    }

    private final Map<Long, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public void setPendingAction(Long telegramId, Action action) {
        pendingInputs.put(telegramId, new PendingInput(action, null));
    }

    public void setPendingActionWithAmount(Long telegramId, Action action, java.math.BigDecimal amount) {
        pendingInputs.put(telegramId, new PendingInput(action, amount));
    }

    public PendingInput getPendingInput(Long telegramId) {
        return pendingInputs.get(telegramId);
    }

    public void clearPendingAction(Long telegramId) {
        pendingInputs.remove(telegramId);
    }
}
