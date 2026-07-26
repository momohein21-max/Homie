package com.homie.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates housemate-written text (announcements, bill descriptions) on
 * demand, using a locally-run Ollama model rather than a paid cloud
 * translation API - Homie's housemates come from different countries, but
 * a student project has no budget for per-request API fees.
 *
 * This talks to Ollama over plain HTTP using the JDK's own built-in
 * java.net.http.HttpClient (available since Java 11) plus a
 * self-constructed Jackson ObjectMapper, rather than Spring's
 * RestTemplate/RestClient helpers - those live in packages that move
 * around between Spring Boot major versions (this project is on Spring
 * Boot 4), and this project's Jackson auto-configuration isn't reliably
 * registering an ObjectMapper bean either. Sticking to plain JDK HTTP +
 * a manually-created ObjectMapper keeps this class working regardless of
 * exactly which Spring Boot web/JSON starter is on the classpath.
 *
 * IMPORTANT DEPLOYMENT NOTE: Ollama has to be running somewhere Homie can
 * reach it over HTTP - by default that's the same machine Homie itself is
 * running on (http://localhost:11434, Ollama's default). That works
 * perfectly for local development and for demoing the app from your own
 * laptop. It does NOT work automatically once Homie is deployed to Render
 * (or any other cloud host) unless Ollama is ALSO reachable from there -
 * Render's own servers don't have Ollama installed, and can't reach a
 * laptop sitting behind a home router. To make translation work on the
 * live deployment, either run Ollama on a server Render can reach and set
 * the OLLAMA_BASE_URL environment variable to its address, or accept that
 * this feature is a "works when demoed locally" feature for now. Either
 * way, the app itself never crashes if Ollama isn't reachable - see the
 * try/catch below.
 */
@Service
public class TranslationService {

    // Where Ollama's API is running. Defaults to Ollama's own default
    // address for local development; override with OLLAMA_BASE_URL for
    // anywhere else Ollama might be reachable (see class comment above).
    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    // Which model to ask Ollama for. Must already be pulled on whichever
    // machine runs Ollama (e.g. "ollama pull llama3.2") - Homie doesn't
    // pull models itself. Override with OLLAMA_MODEL if you're using a
    // different model.
    @Value("${ollama.model}")
    private String ollamaModel;

    // Built directly rather than injected as a Spring bean - this
    // project's Jackson auto-configuration isn't reliably registering an
    // ObjectMapper bean, so this class is self-sufficient instead of
    // depending on it. A plain ObjectMapper is all that's needed here;
    // nothing about this call requires Spring's extra Jackson modules
    // (e.g. for serializing JPA entities), since only a small hand-built
    // request map and Ollama's simple JSON response are ever touched.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public TranslationService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Translates the given text into the given language. Returns the
     * translated text on success.
     *
     * Throws TranslationException with a friendly message if anything
     * goes wrong (Ollama not running, model not pulled, request timed
     * out, etc.) - the controller turns this into a normal JSON error
     * response rather than a raw 500 page.
     */
    public String translate(String text, String targetLanguage) {
        // Kept simple and directive on purpose: this is a translation
        // tool, not a chat assistant, so the prompt explicitly forbids
        // commentary, explanations, or answering anything the text asks -
        // otherwise a model might "helpfully" reply to a question inside
        // an announcement instead of just translating it.
        String prompt = "You are a translation engine, not a conversational assistant. "
                + "Translate the text between the triple-quotes into " + targetLanguage + ". "
                + "Preserve the original meaning, tone, and line breaks. "
                + "Reply with ONLY the translated text - no notes, no explanations, "
                + "no restating the original, no quotation marks around your answer.\n\n"
                + "\"\"\"" + text + "\"\"\"";

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(90)) // local LLM inference can be slow, especially the first call
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new TranslationException(
                        "Ollama returned an error (HTTP " + httpResponse.statusCode() + "). Is the \""
                                + ollamaModel + "\" model pulled?");
            }

            JsonNode json = objectMapper.readTree(httpResponse.body());
            String translated = json.path("response").asText(null);

            if (translated == null || translated.isBlank()) {
                throw new TranslationException(
                        "Ollama didn't return a translation. Is the \"" + ollamaModel + "\" model pulled?");
            }
            return translated.trim();

        } catch (TranslationException e) {
            throw e; // already a friendly message - pass it straight through

        } catch (IOException e) {
            throw new TranslationException(
                    "Couldn't reach Ollama at " + ollamaBaseUrl + ". Make sure Ollama is running "
                            + "(\"ollama serve\") and the \"" + ollamaModel + "\" model is pulled "
                            + "(\"ollama pull " + ollamaModel + "\").");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException("The translation request was interrupted. Please try again.");
        }
    }

    // Thrown when translation can't be completed, with a message that's
    // safe and useful to show directly to the housemate who clicked
    // "Translate".
    public static class TranslationException extends RuntimeException {
        public TranslationException(String message) {
            super(message);
        }
    }
}
