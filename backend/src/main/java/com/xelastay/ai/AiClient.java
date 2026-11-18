package com.xelastay.ai;

/** Modelo de lenguaje que redacta la respuesta. Lanza ResponseStatusException 502/503 si no puede. */
public interface AiClient {

    String generate(String systemInstruction, String prompt);

    default String generateWithTools(String systemInstruction, String prompt, AiToolExecutor executor) {
        return generate(systemInstruction, prompt);
    }
}
