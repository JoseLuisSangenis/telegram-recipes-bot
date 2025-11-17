/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tuempresa.telegramrecipesbot.config;

import com.tuempresa.telegramrecipesbot.bot.BotTelegram;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.logging.Logger;

@Configuration
public class BotConfig {

    private final Logger logger = Logger.getLogger(BotConfig.class.getName());

    @Bean
    public TelegramBotsApi telegramBotsApi(BotTelegram bot,
                                          @Value("${telegram.bot.token:}") String botToken) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        if (botToken == null || botToken.isBlank()) {
            logger.warning("telegram.bot.token vacío — omitiendo registro del bot. Define TELEGRAM_BOT_TOKEN para activar.");
            return botsApi;
        }

        botsApi.registerBot(bot);
        logger.info("Bot registrado en TelegramBotsApi.");
        return botsApi;
    }
}
