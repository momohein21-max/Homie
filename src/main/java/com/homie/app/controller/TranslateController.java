package com.homie.app.controller;

import com.homie.app.dto.TranslateRequestDto;
import com.homie.app.dto.TranslateResponseDto;
import com.homie.app.service.TranslationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A small JSON API behind the "Translate" button on Announcements and
 * Bills: takes a piece of housemate-written text and a target language,
 * and returns it translated (via Ollama - see TranslationService).
 *
 * This is a @RestController rather than a @Controller because it's called
 * from JavaScript (fetch()) on those pages, not from a normal form
 * submission - it returns JSON, not a template name.
 *
 * Every request still requires being logged in, same as the rest of the
 * app - SecurityConfig's default "anyRequest().authenticated()" rule
 * already covers this path, since it isn't in the public permitAll list.
 */
@RestController
public class TranslateController {

    private final TranslationService translationService;

    public TranslateController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/api/translate")
    public ResponseEntity<TranslateResponseDto> translate(@Valid @RequestBody TranslateRequestDto dto,
                                                            BindingResult result) {
        if (result.hasErrors()) {
            String message = result.getFieldError() != null
                    ? result.getFieldError().getDefaultMessage()
                    : "That request wasn't valid.";
            return ResponseEntity.badRequest().body(TranslateResponseDto.failure(message));
        }

        try {
            String translated = translationService.translate(dto.getText(), dto.getTargetLanguage());
            return ResponseEntity.ok(TranslateResponseDto.success(translated));
        } catch (TranslationService.TranslationException e) {
            // A friendly, specific message (e.g. "Ollama isn't running") -
            // safe to show directly on the page, not a stack trace.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(TranslateResponseDto.failure(e.getMessage()));
        }
    }
}
