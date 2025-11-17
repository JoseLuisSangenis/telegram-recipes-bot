/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tuempresa.telegramrecipesbot.controller;

import com.tuempresa.telegramrecipesbot.service.RecipeAIService;
import com.tuempresa.telegramrecipesbot.model.Recipe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DebugController {

    private final RecipeAIService service;

    public DebugController(RecipeAIService service) {
        this.service = service;
    }

    @GetMapping("/debug/recipes")
    public List<Recipe> debugGenerateRecipes(
            @RequestParam String ingredients,
            @RequestParam(defaultValue = "1") int count
    ) throws Exception {
        return service.generateRecipes(ingredients, count);
    }
}