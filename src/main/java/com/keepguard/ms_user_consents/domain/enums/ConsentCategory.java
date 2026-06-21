package com.keepguard.ms_user_consents.domain.enums;

import lombok.Getter;

@Getter
public enum ConsentCategory {
    ESSENTIAL("Essencial", true, 0, true),
    FUNCTIONAL("Funcional", true, 0, false),
    ANALYTICS("Analytics", false, 365, true),
    MARKETING("Marketing", false, 180, true);

    private final String displayName;
    private final boolean mandatory;
    private final int defaultExpirationDays;
    private final boolean canBeRevoked;

    ConsentCategory(String displayName, boolean mandatory, int defaultExpirationDays, boolean canBeRevoked) {
        this.displayName = displayName;
        this.mandatory = mandatory;
        this.defaultExpirationDays = defaultExpirationDays;
        this.canBeRevoked = canBeRevoked;
    }
}

