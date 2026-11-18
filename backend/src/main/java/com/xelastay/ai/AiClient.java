package com.xelastay.ai;

import java.util.function.Consumer;

/** Modelo de lenguaje que redacta la respuesta. Lanza ResponseStatusException 502/503/504 si no puede. */
public interface AiClient {

    String generate(String systemInstruction, String prompt);

    /**
     * Igual que {@link #generate}, pero entrega el texto por partes a {@code onToken} a medida que llega y al final
     * devuelve la respuesta completa. Por defecto genera todo y lo entrega en una sola parte, así un cliente sin
     * streaming sigue funcionando.
     */
    default String generateStream(String systemInstruction, String prompt, Consumer<String> onToken) {
        String answer = generate(systemInstruction, prompt);
        onToken.accept(answer);
        return answer;
    }
}
