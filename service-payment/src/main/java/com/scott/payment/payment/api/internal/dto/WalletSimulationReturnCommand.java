package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletSimulationReturnCommand(
        @NotBlank @Pattern(regexp = "[0-9]{16,32}\\.[a-f0-9]{64}") String returnToken,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,40}") String transactionId) {
    @Override
    public String toString() {
        return "WalletSimulationReturnCommand";
    }
}
