package com.bingo.app.core.config;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.game.repository.GameRepository;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing dev data...");

        // 1. Create Mock User for Frontend Testing
        Long mockTelegramId = 123456789L;
        if (userRepository.findByTelegramId(mockTelegramId).isEmpty()) {
            User mockUser = User.builder()
                    .telegramId(mockTelegramId)
                    .role(Role.ADMIN)
                    .balance(new BigDecimal("1000.00"))
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(mockUser);
            log.info("Created mock user with ID: {}", mockTelegramId);
        }

        // 2. Create a Mock Game if none exists
        if (gameRepository.findFirstByStatusOrderByCreatedAtAsc(GameStatus.WAITING).isEmpty() &&
            gameRepository.findFirstByStatusOrderByStartTimeAsc(GameStatus.STARTED).isEmpty()) {
            
            Game mockGame = Game.builder()
                    .adminId(1L) // Assuming ID 1 or just any ID
                    .status(GameStatus.WAITING)
                    .entryFee(new BigDecimal("50.00"))
                    .maxPlayers(100)
                    .createdAt(LocalDateTime.now())
                    .build();
            gameRepository.save(mockGame);
            log.info("Created mock waiting game for testing.");
        }
    }
}
