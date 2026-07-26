package com.homie.app.dto;

/**
 * What /api/translate sends back to the browser: either the translated
 * text, or an error message to show instead (e.g. Ollama isn't reachable).
 * Only one of the two fields is ever set.
 */
public class TranslateResponseDto {

    private String translatedText;
    private String error;

    public TranslateResponseDto() {
    }

    public static TranslateResponseDto success(String translatedText) {
        TranslateResponseDto dto = new TranslateResponseDto();
        dto.translatedText = translatedText;
        return dto;
    }

    public static TranslateResponseDto failure(String error) {
        TranslateResponseDto dto = new TranslateResponseDto();
        dto.error = error;
        return dto;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public void setTranslatedText(String translatedText) {
        this.translatedText = translatedText;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
