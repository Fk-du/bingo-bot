package com.bingo.app.master.service;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.master.entity.Notification;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.NotificationRepository;
import com.bingo.app.master.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<BingoTelegramBot> botProvider;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final ObjectMapper objectMapper;

    private final ExecutorService telegramExecutor = Executors.newFixedThreadPool(2);

    @Value("${bingo.webapp.url}")
    private String webAppUrl;

    @PreDestroy
    public void shutdown() {
        telegramExecutor.shutdownNow();
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body) {
        return notify(userId, type, title, body, null, null, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String referenceType, Long referenceId) {
        return notify(userId, type, title, body, referenceType, referenceId, null);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public Notification notify(Long userId, String type, String title, String body,
                               String referenceType, Long referenceId, String telegramText) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        Notification saved = notificationRepository.saveAndFlush(notification);
        pushWebSocket(saved);

        if (telegramText != null && !telegramText.isBlank()) {
            sendTelegram(userId, telegramText);
        }

        return saved;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<Notification> getForUser(Long userId, int limit) {
        return limit <= 50
                ? notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .ifPresent(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(java.time.LocalDateTime.now());
                        notificationRepository.save(n);
                    }
                });
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public long markAllRead(Long userId) {
        int updated = notificationRepository.markAllRead(userId);
        return updated;
    }

    private void pushWebSocket(Notification notification) {
        try {
            SimpMessagingTemplate messagingTemplate = messagingTemplateProvider.getIfAvailable();
            if (messagingTemplate == null) return;

            User user = userRepository.findById(notification.getUserId()).orElse(null);
            if (user == null) return;
            String principalName = user.getTelegramId() != null
                    ? String.valueOf(user.getTelegramId()) : String.valueOf(user.getId());

            String payload = objectMapper.writeValueAsString(
                    Map.of("type", "NOTIFICATION", "data", notification));
            messagingTemplate.convertAndSendToUser(principalName, "/queue/notifications", payload);
        } catch (Exception e) {
            log.warn("Failed to push notification via WebSocket: {}", e.getMessage());
        }
    }

    private void sendTelegram(Long userId, String text) {
        telegramExecutor.execute(() -> {
            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || user.getTelegramId() == null) return;
                BingoTelegramBot bot = botProvider.getIfAvailable();
                if (bot == null) return;

                SendMessage message = SendMessage.builder()
                        .chatId(user.getTelegramId())
                        .text(text)
                        .build();

                InlineKeyboardButton launch = new InlineKeyboardButton();
                launch.setText("\uD83D\uDE80 Launch App");
                launch.setWebApp(new WebAppInfo(webAppUrl));

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(List.of(List.of(launch)));
                message.setReplyMarkup(markup);

                bot.execute(message);
            } catch (Exception e) {
                log.debug("Telegram push failed for user {}: {}", userId, e.getMessage());
            }
        });
    }
}