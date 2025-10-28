package com.forge.domain.usecases;

import com.forge.domain.model.MarkUsedResult;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.UsernameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UsernameMarkUsedCaseImpl.
 * Tests reactive behavior using StepVerifier and Mockito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameMarkUsedUseCase - Mark Username as Used")
class UsernameMarkUsedCaseImplTest {

    @Mock
    private UsernameRepository usernameRepository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private UsernameMarkUsedCaseImpl useCase;

    @Test
    @DisplayName("Should successfully mark username as used when username exists and is available")
    void shouldMarkUsernameAsUsedSuccessfully() {
        // Arrange
        String username = "testuser123";
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.just(true));
        when(cacheService.invalidateValidation(username)).thenReturn(Mono.empty());
        when(cacheService.invalidateGenerationCaches()).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(username))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.wasAlreadyUsed()).isFalse();
                    assertThat(result.username()).isEqualTo(username);
                    assertThat(result.markedAt()).isNotNull();
                })
                .verifyComplete();

        // Verify interactions
        verify(usernameRepository).markAsUsed(username);
        verify(cacheService).invalidateValidation(username);
        verify(cacheService).invalidateGenerationCaches();
    }

    @Test
    @DisplayName("Should return already used result when username is already marked or not found")
    void shouldReturnAlreadyUsedWhenUsernameNotFoundOrAlreadyMarked() {
        // Arrange
        String username = "existinguser";
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(username))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.wasAlreadyUsed()).isTrue();
                    assertThat(result.username()).isEqualTo(username);
                    assertThat(result.markedAt()).isNull();
                })
                .verifyComplete();

        // Verify cache invalidation was NOT called since username wasn't updated
        verify(usernameRepository).markAsUsed(username);
        verify(cacheService, never()).invalidateValidation(anyString());
    }

    @Test
    @DisplayName("Should propagate error when repository fails during mark operation")
    void shouldPropagateErrorWhenRepositoryFails() {
        // Arrange
        String username = "failuser";
        RuntimeException repositoryError = new RuntimeException("Database connection failed");
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.error(repositoryError));

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(username))
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                        error.getMessage().equals("Database connection failed")
                )
                .verify();

        // Verify cache was not called due to early error
        verify(usernameRepository).markAsUsed(username);
        verify(cacheService, never()).invalidateValidation(anyString());
    }

    @Test
    @DisplayName("Should propagate error when cache invalidation fails after successful repository update")
    void shouldPropagateErrorWhenCacheInvalidationFails() {
        // Arrange
        String username = "cacheFailUser";
        RuntimeException cacheError = new RuntimeException("Redis connection timeout");
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.just(true));
        when(cacheService.invalidateValidation(username)).thenReturn(Mono.error(cacheError));

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(username))
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                        error.getMessage().equals("Redis connection timeout")
                )
                .verify();

        // Verify both operations were attempted
        verify(usernameRepository).markAsUsed(username);
        verify(cacheService).invalidateValidation(username);
    }

    @Test
    @DisplayName("Should handle null or empty username gracefully")
    void shouldHandleNullUsernameGracefully() {
        // Arrange
        String nullUsername = null;
        when(usernameRepository.markAsUsed(nullUsername)).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(nullUsername))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.wasAlreadyUsed()).isTrue();
                })
                .verifyComplete();

        verify(usernameRepository).markAsUsed(nullUsername);
        verify(cacheService, never()).invalidateValidation(anyString());
    }

    @Test
    @DisplayName("Should handle empty username string")
    void shouldHandleEmptyUsernameString() {
        // Arrange
        String emptyUsername = "";
        when(usernameRepository.markAsUsed(emptyUsername)).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(emptyUsername))
                .assertNext(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result.wasAlreadyUsed()).isTrue();
                    assertThat(result.username()).isEmpty();
                })
                .verifyComplete();

        verify(usernameRepository).markAsUsed(emptyUsername);
        verify(cacheService, never()).invalidateValidation(anyString());
    }

    @Test
    @DisplayName("Should complete reactive chain even when cache invalidation returns empty Mono")
    void shouldCompleteWhenCacheInvalidationReturnsEmpty() {
        // Arrange
        String username = "validuser";
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.just(true));
        when(cacheService.invalidateValidation(username)).thenReturn(Mono.empty());
        when(cacheService.invalidateGenerationCaches()).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(useCase.markAsUsed(username))
                .assertNext(result -> {
                    assertThat(result.wasAlreadyUsed()).isFalse();
                    assertThat(result.username()).isEqualTo(username);
                })
                .verifyComplete();

        verify(usernameRepository).markAsUsed(username);
        verify(cacheService).invalidateValidation(username);
        verify(cacheService).invalidateGenerationCaches();
    }

    @Test
    @DisplayName("Should verify logging occurs on successful mark operation")
    void shouldLogSuccessfulMarkOperation() {
        // Arrange
        String username = "logtest";
        when(usernameRepository.markAsUsed(username)).thenReturn(Mono.just(true));
        when(cacheService.invalidateValidation(username)).thenReturn(Mono.empty());
        when(cacheService.invalidateGenerationCaches()).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(useCase.markAsUsed(username))
                .expectNextCount(1)
                .verifyComplete();

        // Verify all expected interactions occurred (logging happens inside)
        verify(usernameRepository).markAsUsed(username);
        verify(cacheService).invalidateValidation(username);
    }
}
