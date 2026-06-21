package com.keepguard.ms_user_consents.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.consentDocument.ConsentDocumentViewDTO;
import com.keepguard.ms_user_consents.domain.enums.ConsentDocumentStatus;
import com.keepguard.ms_user_consents.domain.enums.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Consent Document Cache Service Tests")
class ConsentDocumentCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ConsentDocumentCacheService cacheService;

    private ConsentDocumentViewDTO viewDTO;
    private UUID documentId;
    private String jsonValue;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();

        viewDTO = ConsentDocumentViewDTO.builder()
                .id(documentId)
                .title("Termos de Uso")
                .description("Descrição")
                .type(ConsentType.TERMS_OF_USE)
                .version(1)
                .status(ConsentDocumentStatus.PUBLISHED)
                .build();

        jsonValue = "{\"id\":\"" + documentId + "\",\"title\":\"Termos de Uso\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Deve cachear documento por ID com sucesso")
    void shouldCacheByIdSuccessfully() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(viewDTO)).thenReturn(jsonValue);

        // When
        cacheService.cacheById(documentId, viewDTO);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq(jsonValue), anyLong(), any());
        verify(redisTemplate, times(1)).opsForValue();
    }

    @Test
    @DisplayName("Deve buscar documento do cache por ID com sucesso")
    void shouldGetByIdFromCacheSuccessfully() throws Exception {
        // Given
        when(valueOperations.get(anyString())).thenReturn(jsonValue);
        when(objectMapper.readValue(jsonValue, ConsentDocumentViewDTO.class)).thenReturn(viewDTO);

        // When
        ConsentDocumentViewDTO result = cacheService.getById(documentId);

        // Then
        assertNotNull(result);
        assertEquals(documentId, result.getId());
        verify(valueOperations, times(1)).get(anyString());
    }

    @Test
    @DisplayName("Deve retornar null quando documento não está em cache")
    void shouldReturnNullWhenDocumentNotInCache() {
        // Given
        when(valueOperations.get(anyString())).thenReturn(null);

        // When
        ConsentDocumentViewDTO result = cacheService.getById(documentId);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Deve remover documento do cache por ID")
    void shouldRemoveByIdFromCache() {
        // When
        cacheService.removeById(documentId);

        // Then
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("Deve cachear última versão publicada por tipo")
    void shouldCacheLatestPublishedByType() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(viewDTO)).thenReturn(jsonValue);

        // When
        cacheService.cacheLatestPublishedByType(ConsentType.TERMS_OF_USE, viewDTO);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq(jsonValue), anyLong(), any());
    }

    @Test
    @DisplayName("Deve buscar última versão publicada por tipo do cache")
    void shouldGetLatestPublishedByTypeFromCache() throws Exception {
        // Given
        when(valueOperations.get(anyString())).thenReturn(jsonValue);
        when(objectMapper.readValue(jsonValue, ConsentDocumentViewDTO.class)).thenReturn(viewDTO);

        // When
        ConsentDocumentViewDTO result = cacheService.getLatestPublishedByType(ConsentType.TERMS_OF_USE);

        // Then
        assertNotNull(result);
        assertEquals(documentId, result.getId());
    }

    @Test
    @DisplayName("Deve limpar todo o cache")
    void shouldClearAllCache() {
        // Given
        Set<String> keys = Set.of("consent_doc_cache:id:1", "consent_doc_cache:id:2");
        when(redisTemplate.keys(anyString())).thenReturn(keys);
        when(redisTemplate.delete(anyCollection())).thenReturn(2L);

        // When
        cacheService.clearAll();

        // Then
        verify(redisTemplate, times(1)).keys(anyString());
        verify(redisTemplate, times(1)).delete(keys);
    }

    @Test
    @DisplayName("Deve retornar null quando lista está vazia no cache")
    void shouldReturnNullWhenListIsEmptyInCache() {
        // Given
        when(valueOperations.get(anyString())).thenReturn("");

        // When
        List<ConsentDocumentViewDTO> result = cacheService.getAllPublished();

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Deve cachear lista de documentos publicados")
    void shouldCacheAllPublished() throws Exception {
        // Given
        List<ConsentDocumentViewDTO> documents = List.of(viewDTO);
        when(objectMapper.writeValueAsString(documents)).thenReturn(jsonValue);

        // When
        cacheService.cacheAllPublished(documents);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq(jsonValue), anyLong(), any());
    }
}

