package com.bingo.app.modules.bot.handler;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.user.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.bingo.app.modules.bot.handler.impl.PlayerBotHandler;
import com.bingo.app.modules.bot.handler.impl.AdminBotHandler;
import com.bingo.app.modules.bot.handler.impl.SuperAdminBotHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallbackRouter {

    private final PlayerBotHandler playerHandler;
    private final AdminBotHandler adminHandler;
    private final SuperAdminBotHandler superAdminHandler;

    public void route(CallbackContext ctx) {

        String data = ctx.getData();
        Role role = ctx.getUser().getRole();

        log.debug("Routing callback '{}' for role {}", data, role);

        switch (role) {
            case PLAYER -> playerHandler.handle(ctx);
            case ADMIN -> adminHandler.handle(ctx);
            case SUPER_ADMIN -> superAdminHandler.handle(ctx);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        }
    }
}