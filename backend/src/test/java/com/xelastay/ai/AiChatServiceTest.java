package com.xelastay.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xelastay.ai.KnowledgeSearch.ChunkHit;
import com.xelastay.ai.KnowledgeSearch.PropertyHit;
import com.xelastay.ai.dto.ChatResponse;
import com.xelastay.ai.dto.SourceRef;
import com.xelastay.catalog.Location;
import com.xelastay.property.Property;
import com.xelastay.property.PropertyRepository;
import com.xelastay.property.PropertyStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    private static final String QUESTION = "¿Hasta cuándo puedo cancelar?";

    @Mock
    private KnowledgeSearch search;

    @Mock
    private AiClient aiClient;

    @Mock
    private AiLogWriter logWriter;

    @Mock
    private PropertyRepository propertyRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID chunkId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private AiChatService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties("clave", "gemini-3.8-flash",
                "https://generativelanguage.googleapis.com/v1beta", Duration.ofSeconds(30), 2048, 5, 3, 800, 100);
        service = new AiChatService(search, aiClient, logWriter, properties, propertyRepository, new ObjectMapper());
    }

    @Test
    void answersWithNumberedSourcesFromDocumentsAndProperties() {
        when(search.searchChunks(QUESTION, 5)).thenReturn(List.of(chunk()));
        when(search.searchProperties(QUESTION, 3)).thenReturn(List.of(new PropertyHit(propertyId, "Casa en Xela",
                new BigDecimal("350"), "GTQ", 4, "Quetzaltenango", "Quetzaltenango", 0.2)));
        when(aiClient.generateWithTools(eq(AiChatService.SYSTEM_INSTRUCTION), any(), any(AiToolExecutor.class)))
                .thenReturn("Hasta 48 horas antes [1].");
        UUID interactionId = UUID.randomUUID();
        when(logWriter.recordChat(eq(userId), eq(QUESTION), eq("Hasta 48 horas antes [1]."), any(),
                eq("127.0.0.1"), eq("curl"))).thenReturn(interactionId);

        ChatResponse response = service.chat(userId, "  " + QUESTION + " ", "127.0.0.1", "curl");

        assertThat(response.interactionId()).isEqualTo(interactionId);
        assertThat(response.answer()).isEqualTo("Hasta 48 horas antes [1].");
        assertThat(response.sources()).hasSize(2);
        SourceRef document = response.sources().get(0);
        assertThat(document.ref()).isEqualTo(1);
        assertThat(document.type()).isEqualTo(SourceRef.DOCUMENT);
        assertThat(document.documentId()).isEqualTo(documentId);
        assertThat(document.chunkId()).isEqualTo(chunkId);
        SourceRef property = response.sources().get(1);
        assertThat(property.ref()).isEqualTo(2);
        assertThat(property.type()).isEqualTo(SourceRef.PROPERTY);
        assertThat(property.propertyId()).isEqualTo(propertyId);
        assertThat(property.excerpt()).contains("Q350.00 por noche");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiClient).generateWithTools(eq(AiChatService.SYSTEM_INSTRUCTION), prompt.capture(),
                any(AiToolExecutor.class));
        assertThat(prompt.getValue())
                .contains("[1] Documento \"Políticas de cancelación\" (fragmento 1)")
                .contains("La cancelación es gratuita hasta 48 horas antes.")
                .contains("[2] Alojamiento \"Casa en Xela\"")
                .endsWith("PREGUNTA:\n" + QUESTION);
    }

    @Test
    void withoutSourcesItStillCallsTheModelWithTheToolAndLogs() {
        when(aiClient.generateWithTools(any(), any(), any(AiToolExecutor.class)))
                .thenReturn(AiChatService.NO_SOURCES_ANSWER);
        when(logWriter.recordChat(any(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        ChatResponse response = service.chat(userId, "¿Qué clima hace en Marte?", "127.0.0.1", null);

        assertThat(response.answer()).isEqualTo(AiChatService.NO_SOURCES_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(aiClient).generateWithTools(eq(AiChatService.SYSTEM_INSTRUCTION), any(), any(AiToolExecutor.class));
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void toolReadsPublishedPropertiesAndReturnsThemToTheModel() {
        Property property = mock(Property.class);
        Location location = mock(Location.class);
        when(property.getTitle()).thenReturn("Casa en Xela");
        when(property.getPricePerNight()).thenReturn(new BigDecimal("350.00"));
        when(property.getCurrency()).thenReturn("GTQ");
        when(property.getLocation()).thenReturn(location);
        when(location.getMunicipality()).thenReturn("Quetzaltenango");
        when(location.getDepartment()).thenReturn("Quetzaltenango");
        when(propertyRepository.findByStatusAndDeletedAtIsNullOrderByTitleAsc(PropertyStatus.PUBLISHED))
                .thenReturn(List.of(property));
        when(aiClient.generateWithTools(any(), any(), any(AiToolExecutor.class))).thenAnswer(invocation -> {
            AiToolExecutor executor = invocation.getArgument(2);
            return executor.execute(AiToolExecutor.CONSULTAR_ALOJAMIENTOS_DISPONIBLES);
        });
        when(logWriter.recordChat(any(), any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        ChatResponse response = service.chat(userId, "¿Qué alojamientos hay?", "127.0.0.1", "curl");

        verify(propertyRepository).findByStatusAndDeletedAtIsNullOrderByTitleAsc(PropertyStatus.PUBLISHED);
        assertThat(response.answer())
                .contains("Casa en Xela")
                .contains("350.00")
                .contains("Quetzaltenango")
                .contains("[T]");
    }

    @Test
    void modelErrorsAreNotLoggedAsAnswers() {
        when(search.searchChunks(any(), anyInt())).thenReturn(List.of(chunk()));
        when(aiClient.generateWithTools(any(), any(), any(AiToolExecutor.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Límite de uso"));

        assertThatThrownBy(() -> service.chat(userId, QUESTION, "127.0.0.1", null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(logWriter);
    }

    private ChunkHit chunk() {
        return new ChunkHit(chunkId, documentId, "Políticas de cancelación", "POLITICA", 0,
                "La cancelación es gratuita hasta 48 horas antes.", 0.9);
    }
}
