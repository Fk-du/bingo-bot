package com.bingo.app.bot.handler;

import com.bingo.app.bot.callback.CallbackContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlayerBotHandler {

    public void handle(CallbackContext ctx) {
        log.info("PlayerBotHandler handling: {}", ctx.getData());
    }
}
