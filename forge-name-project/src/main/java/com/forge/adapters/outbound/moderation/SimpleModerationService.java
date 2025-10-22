package com.forge.adapters.outbound.moderation;

import com.forge.domain.ports.outboung.ModerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Simple word-based moderation service used as fallback when external APIs are unavailable.
 * <p>
 * This service provides basic content filtering by checking usernames against a blacklist
 * of inappropriate terms. It is NOT a replacement for ML-based moderation services like
 * Perspective API or OpenAI, but serves as a reasonable fallback for high-availability scenarios.
 * </p>
 *
 * <p><b>Note:</b> This is NOT a Spring bean. It is instantiated directly by other moderation
 * services (OpenAI, Perspective) as a fallback mechanism.</p>
 *
 * <h3>Limitations:</h3>
 * <ul>
 *   <li>No context awareness - blocks words regardless of usage</li>
 *   <li>Simple substring matching - can be bypassed with character substitution</li>
 *   <li>Static list - requires code changes to update</li>
 *   <li>No severity scoring - binary appropriate/inappropriate decision</li>
 * </ul>
 *
 * <h3>Recommendations for Production:</h3>
 * <ul>
 *   <li>Move blacklist to database for dynamic updates</li>
 *   <li>Implement character normalization (l33t speak, accents, etc.)</li>
 *   <li>Add whitelist for legitimate uses of flagged terms</li>
 *   <li>Consider using a dedicated profanity detection library</li>
 * </ul>
 */
@Slf4j
@Service
public class SimpleModerationService implements ModerationService {

    /**
     * Blacklist of inappropriate terms for username validation.
     * Organized by category for maintainability.
     * <p>
     * Note: This list contains offensive language necessary for content moderation.
     * Terms are stored in lowercase for case-insensitive matching.
     * </p>
     */
    private static final Set<String> BLACKLIST = Set.of(
            // === English offensive terms ===
            "fuck", "shit", "bitch", "asshole", "bastard", "damn",
            "cunt", "dick", "pussy", "cock", "whore", "slut",

            // === Spanish offensive terms (Insultos y vulgaridades) ===
            "puto", "puta", "mierda", "cabron", "cabrona", "pendejo",
            "pendeja", "idiota", "imbecil", "estupido", "estupida",
            "joder", "jodido", "cono", "coño", "hostia", "polla",
            "verga", "chingar", "chingada", "pinche", "carajo",
            "culero", "culera", "hijueputa",
            "gonorrea", "malparido", "malparida", "boludo", "pelotudo",
            "concha", "conchudo", "huevon", "huevona", "gilipollas",
            "capullo", "mamaguevo", "mamada", "mamador", "zorra",
            "cerdo", "cerda", "basura", "escoria", "mierdero",

            // === Hate speech and discrimination (Spanish and English) ===
            "nazi", "hitler", "terrorista", "terrorist", "fascista",
            "racista", "racist", "supremacista", "supremacist",

            // === Identity-based slurs ===
            "marica", "maricon", "tortillera", "travesti",
            "faggot", "tranny", "dyke", "retard", "retarded",

            // === Sexual content ===
            "porn", "porno", "xxx", "sex", "sexo", "nude", "desnudo",
            "viagra", "penis", "vagina", "semen", "orgasm", "orgasmo",

            // === Spam and commercial terms ===
            "spam", "casino", "crypto", "bitcoin",
            "lottery", "prize", "winner", "loteria", "premio",

            // === Reserved system terms ===
            "admin", "administrator", "root", "system", "moderator",
            "support", "official", "staff", "employee", "bot",
            "test", "demo", "null", "undefined",

            // === Security-related terms ===
            "password", "passwd", "secret", "token", "apikey",
            "api_key", "access_token", "private_key", "credential"
    );

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        return Mono.fromSupplier(() -> {
            if (username == null || username.isBlank()) {
                log.debug("Null or blank username provided to SimpleModerationService");
                return false;
            }

            String normalizedUsername = username.toLowerCase().trim();

            for (String badWord : BLACKLIST) {
                if (normalizedUsername.contains(badWord)) {
                    log.warn("SimpleModerationService flagged username '{}' for containing: '{}'",
                            username, badWord);
                    return false;
                }
            }

            log.debug("SimpleModerationService approved username: '{}'", username);
            return true;
        });
    }

}