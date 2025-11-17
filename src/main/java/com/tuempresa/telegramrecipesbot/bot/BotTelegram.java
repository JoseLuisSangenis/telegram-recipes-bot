/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tuempresa.telegramrecipesbot.bot;

import com.tuempresa.telegramrecipesbot.model.Recipe;
import com.tuempresa.telegramrecipesbot.service.RecipeAIService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BotTelegram extends TelegramLongPollingBot {

    private static final Logger logger = Logger.getLogger(BotTelegram.class.getName());
    private static final Random RANDOM = new Random();

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token:}")
    private String botToken;

    private final RecipeAIService recipeAIService;

    public BotTelegram(RecipeAIService recipeAIService) {
        this.recipeAIService = recipeAIService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = update.getMessage().getChatId().toString();
        String text = update.getMessage().getText().trim();

        try {
            // Intentar extraer una cantidad solicitada por el usuario (1..3)
            int requested = parseRequestedCount(text);
            int count;
            String infoMsg;

            if (requested > 0) {
                // Si el usuario pidió >3 lo capamos a 3 y avisamos
                if (requested > 3) {
                    count = 3;
                    infoMsg = "Pediste " + requested + " recetas. Generaré un máximo de 3 recetas.";
                } else if (requested < 1) {
                    count = 1;
                    infoMsg = "Cantidad solicitada inválida. Generaré 1 receta.";
                } else {
                    count = requested;
                    infoMsg = "Generaré " + count + " receta(s) como pediste.";
                }
            } else {
                // Si no pidió, elegimos aleatoriamente 1..3
                count = RANDOM.nextInt(3) + 1; // 1..3
                infoMsg = "Recibí tus ingredientes. Generando " + count + " receta(s)...";
            }

            // ACK informativo (si ya usamos infoMsg para notificar cantidad)
            executeSafe(new SendMessage(chatId, infoMsg));

            // Generar recetas (delegado al servicio)
            List<Recipe> recipes = recipeAIService.generateRecipes(text, count);

            // Enviar cada receta por separado
            for (Recipe r : recipes) {
                StringBuilder sb = new StringBuilder();
                sb.append("*").append(r.getTitle()).append("*\n\n");
                sb.append(r.getDescription());
                String texto = escapeMarkdownV2(sb.toString());

                // Truncar por seguridad (Telegram max 4096). Dejamos margen.
                if (texto.length() > 4000) {
                    texto = texto.substring(0, 3997) + "...";
                }

                SendMessage msg = new SendMessage(chatId, texto);
                msg.setParseMode("MarkdownV2");
                executeSafe(msg);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generando recetas", e);
            try {
                executeSafe(new SendMessage(chatId, "Error generando recetas: " + e.getMessage()));
            } catch (Exception ignored) {}
        }
    }

    private void executeSafe(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            logger.log(Level.SEVERE, "Telegram API error sending message", e);
        }
    }

    /**
     * Intenta parsear la cantidad solicitada por el usuario a partir del texto del mensaje.
     * Soporta:
     *  - números explícitos: "1", "2", "3", "5 recetas"
     *  - palabras básicas en español: "uno", "una", "dos", "tres"
     * Devuelve 0 si no detecta ninguna cantidad (para indicar "usar valor por defecto/aleatorio").
     */
    private int parseRequestedCount(String text) {
        if (text == null || text.isBlank()) return 0;

        String lower = text.toLowerCase();

        // Mapa simple de palabras a números (spanish)
        String[][] words = {
                {"uno", "1"}, {"una", "1"}, {"un", "1"},
                {"dos", "2"},
                {"tres", "3"},
                {"cuatro", "4"}, {"cinco", "5"} // por si pedís más; luego lo capamos
        };
        for (String[] pair : words) {
            if (lower.contains(" " + pair[0] + " ") || lower.endsWith(" " + pair[0]) || lower.startsWith(pair[0] + " ")
                    || lower.matches(".*\\b" + Pattern.quote(pair[0]) + "\\b.*")) {
                try {
                    return Integer.parseInt(pair[1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Buscar número explícito en el texto
        Pattern p = Pattern.compile("\\b([1-9][0-9]?)\\b");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }

        // No se detectó cantidad explícita
        return 0;
    }

    /**
     * Escapa caracteres especiales para MarkdownV2.
     */
    private String escapeMarkdownV2(String text) {
        if (text == null) return "";
        return text.replaceAll("([_\\*\\[\\]\\(\\)~`>#+\\-=|{}.!])", "\\\\$1");
    }
}
