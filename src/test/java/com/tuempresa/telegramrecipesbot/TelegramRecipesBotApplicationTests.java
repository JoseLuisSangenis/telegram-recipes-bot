package com.tuempresa.telegramrecipesbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = TelegramRecipesBotApplication.class)
class TelegramRecipesBotApplicationTests {

    @MockBean
    private org.telegram.telegrambots.meta.TelegramBotsApi telegramBotsApi;

    @Test
    void contextLoads() {
    }
}
