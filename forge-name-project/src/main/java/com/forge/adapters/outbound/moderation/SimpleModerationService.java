package com.forge.adapters.outbound.moderation;

import com.forge.domain.ports.outboung.ModerationService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
public class SimpleModerationService implements ModerationService {

    // Simplified blacklist - in production use OpenAI/Perspective APIs
    private static final Set<String> BLACKLIST = Set.of(
            "badword",
            "offensive",
            "inappropriate",
            "spam",
            "admin",
            "root",
            "system"
    );

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        return Mono.fromSupplier(() -> {
            String loserUsername = username.toLowerCase();

            for (String badWord : BLACKLIST) {
                if (loserUsername.contains(badWord)) {
                    return false;
                }
            }

            return true;
        });
    }

}