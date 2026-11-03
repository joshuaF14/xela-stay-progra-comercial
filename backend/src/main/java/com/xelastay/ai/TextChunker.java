package com.xelastay.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Divide un texto en fragmentos de {@code chunkWords} palabras que se solapan {@code overlapWords} palabras,
 * para que una idea que cae entre dos fragmentos aparezca completa en al menos uno. Conserva los saltos de línea
 * y el formato Markdown originales.
 */
public final class TextChunker {

    private static final Pattern WORD_WITH_SPACE = Pattern.compile("\\S+\\s*");

    private TextChunker() {
    }

    public static List<String> split(String text, int chunkWords, int overlapWords) {
        if (chunkWords < 1 || overlapWords < 0 || overlapWords >= chunkWords) {
            throw new IllegalArgumentException("Se requiere chunkWords > overlapWords >= 0");
        }
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_WITH_SPACE.matcher(text.strip());
        while (matcher.find()) {
            words.add(matcher.group());
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkWords - overlapWords;
        for (int start = 0; start < words.size(); start += step) {
            int end = Math.min(start + chunkWords, words.size());
            chunks.add(String.join("", words.subList(start, end)).strip());
            if (end == words.size()) {
                break;
            }
        }
        return chunks;
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.strip().split("\\s+").length;
    }
}
