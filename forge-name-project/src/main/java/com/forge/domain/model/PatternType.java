package com.forge.domain.model;

import java.util.concurrent.ThreadLocalRandom;

public enum PatternType {
    CLASSIC(0.7),
    SEPARATOR(0.2),
    WORDPLAY(0.1);

    private final double probability;

    PatternType(double probability) {
        this.probability = probability;
    }

    public double getProbability() {
        return probability;
    }

    public static PatternType selectRandom() {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for(PatternType patternType : values()) {
            cumulative += patternType.probability;
            if(random <= cumulative) {
                return patternType;
            }
        }

        return CLASSIC;
    }
}
