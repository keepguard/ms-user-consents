package com.keepguard.ms_user_consents.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepguard.ms_user_consents.application.dto.userConsent.UserConsentViewDTO;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("User Consent Cache Service Tests")
class UserConsentCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserConsentCacheService cacheService;

    private UserConsentViewDTO viewDTO;
    private UUID userId;
    private UUID consentId;
    private UUID consentDocumentId;
    private String jsonValue;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        consentId = UUID.randomUUID();
        consentDocumentId = UUID.randomUUID();

        viewDTO = UserConsentViewDTO.builder()
                .id(consentId)
                .userId(userId)
                .consentDocumentId(consentDocumentId)
                .version(1)
                .acceptedAt(LocalDateTime.now())
                .build();

        jsonValue = "{\"id\":\"" + consentId + "\",\"userId\":\"" + userId + "\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Deve cachear consentimento por userId e consentDocumentId")
    void shouldCacheLatestByUserIdAndConsentDocumentId() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(viewDTO)).thenReturn(jsonValue);

        // When
        cacheService.cacheLatestByUserIdAndConsentDocumentId(userId, consentDocumentId, viewDTO);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq(jsonValue), anyLong(), any());
    }

    @Test
    @DisplayName("Deve buscar último consentimento do cache")
    void shouldGetLatestFromCache() throws Exception {
        // Given
        when(valueOperations.get(anyString())).thenReturn(jsonValue);
        when(objectMapper.readValue(jsonValue, UserConsentViewDTO.class)).thenReturn(viewDTO);

        // When
        UserConsentViewDTO result = cacheService.getLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Then
        assertNotNull(result);
        assertEquals(consentId, result.getId());
        assertEquals(userId, result.getUserId());
    }

    @Test
    @DisplayName("Deve cachear resultado de hasAccepted")
    void shouldCacheHasAccepted() {
        // When
        cacheService.cacheHasAccepted(userId, consentDocumentId, 1, true);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq("true"), anyLong(), any());
    }

    @Test
    @DisplayName("Deve buscar resultado de hasAccepted do cache")
    void shouldGetHasAcceptedFromCache() {
        // Given
        when(valueOperations.get(anyString())).thenReturn("true");

        // When
        Boolean result = cacheService.hasAccepted(userId, consentDocumentId, 1);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Deve retornar null quando hasAccepted não está em cache")
    void shouldReturnNullWhenHasAcceptedNotInCache() {
        // Given
        when(valueOperations.get(anyString())).thenReturn(null);

        // When
        Boolean result = cacheService.hasAccepted(userId, consentDocumentId, 1);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Deve cachear lista de consentimentos por userId")
    void shouldCacheByUserId() throws Exception {
        // Given
        List<UserConsentViewDTO> consents = List.of(viewDTO);
        when(objectMapper.writeValueAsString(consents)).thenReturn(jsonValue);

        // When
        cacheService.cacheByUserId(userId, consents);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq(jsonValue), anyLong(), any());
    }

    @Test
    @DisplayName("Deve buscar lista de consentimentos por userId do cache")
    void shouldGetByUserIdFromCache() throws Exception {
        // Given
        List<UserConsentViewDTO> consents = List.of(viewDTO);
        when(valueOperations.get(anyString())).thenReturn(jsonValue);
        when(objectMapper.readValue(eq(jsonValue), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(consents);

        // When
        List<UserConsentViewDTO> result = cacheService.getByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Deve remover consentimento do cache")
    void shouldRemoveFromCache() {
        // When
        cacheService.removeLatestByUserIdAndConsentDocumentId(userId, consentDocumentId);

        // Then
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("Deve limpar todo o cache de consentimentos")
    void shouldClearAllCache() {
        // Given
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("key1", "key2"));
        when(redisTemplate.delete(anyCollection())).thenReturn(2L);

        // When
        cacheService.clearAll();

        // Then
        verify(redisTemplate, times(1)).keys(anyString());
        verify(redisTemplate, times(1)).delete(anyCollection());
    }
}

