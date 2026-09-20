package com.catalogstudio.extension.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PairRequest(
        @JsonProperty("pairingKey")
        @JsonAlias({"pairing_key", "key", "extensionKey"})
        String pairingKey,
        String deviceName
) {
    public PairRequest {
        pairingKey = pairingKey == null ? null : pairingKey.trim();
        deviceName = deviceName == null || deviceName.isBlank() ? "Chrome" : deviceName.trim();
    }
}
