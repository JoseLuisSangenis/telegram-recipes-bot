/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tuempresa.telegramrecipesbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.telegramrecipesbot.model.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class RecipeAIService {

    private static final Logger logger = Logger.getLogger(RecipeAIService.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${openrouter.api.key:}")
    private String openrouterApiKey;

    // Modelo por defecto: google/gemma-2-9b-it (suele tener requests gratuitos limitados)
    @Value("${openrouter.model:google/gemma-2-9b-it}")
    private String openrouterModel;

    private static final String OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/responses";

    /**
     * Genera recetas usando OpenRouter Responses API.
     * Mantiene la firma que usa el resto del proyecto.
     */
    public List<Recipe> generateRecipes(String ingredientes, int count) throws Exception {
        // Prompt estricto pidiendo solo JSON
        String prompt = "Eres un chef profesional. Dados los ingredientes: \"" + ingredientes + "\" genera " + count +
                " recetas. Responde SOLO con JSON VÁLIDO EXACTO en este formato: " +
                "[{ \"title\":\"...\", \"description\":\"...\" }]";

        // Construimos el body en formato compatible (OpenRouter / Responses)
        Map<String, Object> message = new HashMap<>();
        message.put("type", "message");
        message.put("role", "user");
        message.put("content", prompt);

        List<Object> inputList = new ArrayList<>();
        inputList.add(message);

        Map<String, Object> body = new HashMap<>();
        body.put("input", inputList);
        body.put("model", openrouterModel);

        // Parámetros de generación conservadores para ahorrar tokens
        Map<String, Object> generation = new HashMap<>();
        generation.put("temperature", 0.2);
        generation.put("max_output_tokens", 300);
        body.put("generation", generation);

        String jsonBody = mapper.writeValueAsString(body);

        int maxRetries = 3;
        int attempt = 0;

        while (true) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(OPENROUTER_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(60_000);

                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "telegram-recipes-bot/1.0");

                if (openrouterApiKey != null && !openrouterApiKey.isBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer " + openrouterApiKey);
                } else {
                    logger.warning("OPENROUTER_API_KEY no configurada. La llamada puede fallar por falta de auth.");
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                String respText = sb.toString();

                if (status == 429) {
                    if (attempt > maxRetries) throw new Exception("Rate limit (429) en OpenRouter: " + respText);
                    logger.info("Rate limited; reintentando intento " + attempt);
                    Thread.sleep(1000L * attempt);
                    continue;
                }

                if (status == 401 || status == 403) {
                    throw new Exception("Auth error OpenRouter (status=" + status + "): " + respText);
                }

                if (status < 200 || status >= 300) {
                    throw new Exception("Error OpenRouter (status=" + status + "): " + respText);
                }

                // Extraer texto del output (varios fallbacks)
                String textOutput = extractTextFromOpenRouterResponse(respText);

                // Extraer JSON array del texto (primer '[' al último ']')
                int start = textOutput.indexOf('[');
                int end = textOutput.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    String jsonArray = textOutput.substring(start, end + 1);
                    return mapper.readValue(jsonArray, new TypeReference<List<Recipe>>() {});
                } else {
                    // Intentar parsear objeto único
                    try {
                        Recipe single = mapper.readValue(textOutput, Recipe.class);
                        return Collections.singletonList(single);
                    } catch (Exception exSingle) {
                        logger.log(Level.SEVERE, "No pude extraer JSON array desde OpenRouter. Raw: " + textOutput);
                        throw new Exception("No pude extraer un array JSON de la respuesta de OpenRouter. Raw: " + textOutput);
                    }
                }

            } catch (Exception ex) {
                if (attempt >= maxRetries) throw ex;
                logger.log(Level.WARNING, "Intento " + attempt + " fallido: " + ex.getMessage() + ". Reintentando...");
                Thread.sleep(1000L * attempt);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    /**
     * Extrae texto de la respuesta de OpenRouter. Busca 'output' -> content -> text,
     * o 'generated_text', o texto bruto.
     */
    private String extractTextFromOpenRouterResponse(String respText) {
        try {
            JsonNode root = mapper.readTree(respText);

            // 1) 'output' array estilo Responses API
            if (root.has("output") && root.get("output").isArray() && root.get("output").size() > 0) {
                JsonNode out0 = root.get("output").get(0);
                if (out0.has("content") && out0.get("content").isArray()) {
                    StringBuilder tmp = new StringBuilder();
                    for (JsonNode c : out0.get("content")) {
                        if (c.has("text")) tmp.append(c.get("text").asText());
                        else if (c.isTextual()) tmp.append(c.asText());
                        else if (c.has("type") && c.has("content") && c.get("content").isTextual()) {
                            tmp.append(c.get("content").asText());
                        }
                    }
                    String t = tmp.toString().trim();
                    if (!t.isEmpty()) return t;
                }
                if (out0.has("text")) return out0.get("text").asText();
            }

            // 2) Fallbacks comunes
            if (root.has("generated_text")) return root.get("generated_text").asText();
            if (root.has("result")) return root.get("result").asText();
            if (root.isTextual()) return root.asText();

        } catch (Exception e) {
            logger.log(Level.FINE, "Fallo parseo JSON OpenRouter: " + e.getMessage());
        }
        // último recurso: devolver raw
        return respText;
    }
}
