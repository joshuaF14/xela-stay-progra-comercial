package com.xelastay.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void splitsIntoOverlappingChunks() {
        List<String> chunks = TextChunker.split(words(1700), 800, 100);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).startsWith("w0 ").endsWith("w799");
        assertThat(chunks.get(1)).startsWith("w700 ").endsWith("w1499");
        assertThat(chunks.get(2)).startsWith("w1400 ").endsWith("w1699");
        assertThat(TextChunker.countWords(chunks.get(0))).isEqualTo(800);
    }

    @Test
    void shortTextIsASingleChunkWithItsFormatting() {
        String markdown = "# Reglas\n\n- Check-in: 15:00\n- Check-out: 11:00";

        assertThat(TextChunker.split(markdown, 800, 100)).containsExactly(markdown);
    }

    @Test
    void blankTextHasNoChunks() {
        assertThat(TextChunker.split("  \n\t ", 800, 100)).isEmpty();
        assertThat(TextChunker.split(null, 800, 100)).isEmpty();
    }

    @Test
    void overlapMustBeSmallerThanChunk() {
        assertThatThrownBy(() -> TextChunker.split("hola", 100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String words(int count) {
        return IntStream.range(0, count).mapToObj(i -> "w" + i).collect(Collectors.joining(" "));
    }
}
