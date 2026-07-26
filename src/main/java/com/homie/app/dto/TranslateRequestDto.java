package com.homie.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Carries what the "Translate" widget on Announcements/Bills sends to
 * /api/translate: the text a housemate is looking at, and which language
 * they want to read it in (e.g. "Spanish", "Polish", "Mandarin Chinese").
 *
 * targetLanguage is a free-text field rather than a fixed dropdown of
 * codes, since Homie is meant to work for housemates from ANY country -
 * a fixed list would always be missing someone's language. The AI model
 * doing the translating understands plain language names directly, so
 * there's no need to map to ISO codes first.
 */
public class TranslateRequestDto {

    @NotBlank(message = "There's no text to translate.")
    private String text;

    @NotBlank(message = "Please choose a language to translate into.")
    private String targetLanguage;

    public TranslateRequestDto() {
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }
}
