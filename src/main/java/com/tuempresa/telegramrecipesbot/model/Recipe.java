/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.tuempresa.telegramrecipesbot.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Recipe {
    private String title;
    private String description;

    // Opcional
    @JsonProperty("image_prompt")
    private String imagePrompt;

    // getters y setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImagePrompt() { return imagePrompt; }
    public void setImagePrompt(String imagePrompt) { this.imagePrompt = imagePrompt; }
}