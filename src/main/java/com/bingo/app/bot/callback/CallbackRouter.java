package com.bingo.app.bot.callback;

import com.bingo.app.master.enums.Role;
import com.bingo.app.bot.handler.AdminBotHandler;
import com.bingo.app.bot.handler.PlayerBotHandler;
import com.bingo.app.bot.handler.SuperAdminBotHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackRouter {

    private final PlayerBotHandler playerBotHandler;
    private final AdminBotHandler adminBotHandler;
    private final SuperAdminBotHandler superAdminBotHandler;

    public void route(CallbackContext ctx) {
        String data = ctx.getData();
        Role role = ctx.getUser().getRole();

        log.debug("Routing callback '{}' for role {}", data, role);

        switch (role) {
            case PLAYER -> playerBotHandler.handle(ctx);
            case ADMIN -> adminBotHandler.handle(ctx);
            case SUPER_ADMIN -> superAdminBotHandler.handle(ctx);
            default -> log.warn("Unknown role: {}", role);
        }
    }
}
