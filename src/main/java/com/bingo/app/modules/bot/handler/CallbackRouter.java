package com.bingo.app.modules.bot.handler;

import com.bingo.app.modules.bot.context.CallbackContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// These will be recreated in the new structure
import com.bingo.app.modules.bot.handler.impl.PlayerBotHandler;
import com.bingo.app.modules.bot.handler.impl.AdminBotHandler;
import com.bingo.app.modules.bot.handler.impl.SuperAdminBotHandler;

@Component
@RequiredArgsConstructor
public class CallbackRouter {

    private final PlayerBotHandler playerHandler;
    private final AdminBotHandler adminHandler;
    private final SuperAdminBotHandler superAdminHandler;

    public void route(CallbackContext ctx) {

        String data = ctx.getData();

        if (data.startsWith("PLAYER_")) {
            playerHandler.handle(ctx);
            return;
        }

        if (data.startsWith("ADMIN_")) {
            adminHandler.handle(ctx);
            return;
        }

        if (data.startsWith("SUPER_")) {
            superAdminHandler.handle(ctx);
            return;
        }

        throw new IllegalArgumentException("Unknown callback: " + data);
    }
}