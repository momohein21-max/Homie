package com.homie.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Carries the data typed into the "New notice" form on the Announcements
 * board.
 */
public class AnnouncementCreateDto {

    @NotBlank(message = "Please enter a title")
    private String title;

    @NotBlank(message = "Please choose a category")
    private String category;

    @NotBlank(message = "Please write the notice")
    private String body;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
