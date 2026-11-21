package com.scott.payment.capability.governance;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/** 商户支付方式控制台协议。申请、授权与配置就绪各自表达。 */
public final class MerchantPaymentModels {
    private MerchantPaymentModels() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Search extends PageRequest {
        @Size(max = 32)
        private String merchantId;
        @Size(max = 64)
        private String itemKey;
        @Size(max = 32)
        private String status;
    }

    public record MerchantOption(String merchantId, String merchantName) {
    }

    public record Apply(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}") String requestId,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 64) String> items,
            @Size(max = 1000) String reason) {
    }

    public record Decision(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}") String requestId,
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedVersion,
            @NotBlank @Pattern(regexp = "APPROVE|RETURN|REJECT") String decision,
            @Size(max = 1000) String reason) {
    }

    public record Change(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}") String requestId,
            @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String expectedVersion,
            @Size(max = 1000) String reason,
            LocalDateTime effectiveTime,
            LocalDateTime expireTime) {
    }

    public record BatchDecision(@NotEmpty @Size(max = 100) List<@NotNull @Valid DecisionItem> items) {
    }

    public record DecisionItem(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String itemId,
            @NotNull @Valid Decision command) {
    }

    public record DecisionOutcome(String itemId, String outcome, ItemReceipt receipt, String code, String message) {
    }

    public record BatchDecisionReceipt(List<DecisionOutcome> results, int succeeded, int failed,
                                       int unknown, int unprocessed) {
    }

    public record BaselineSubmit(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}") String requestId,
            @NotNull LocalDateTime cutoffTime,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Evidence> items,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record Evidence(
            @NotBlank @Size(max = 64) String itemKey,
            @NotBlank @Pattern(regexp = "SUCCESSFUL_PAYMENT|HISTORICAL_CONFIG") String evidenceType,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:#/-]{1,160}") String evidenceRef,
            @NotNull LocalDateTime evidenceTime) {
    }

    public record BaselineApprove(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}") String requestId,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 64) String> approvedItemKeys,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record BaselineReceipt(String batchRef, int itemCount, int unresolvedCount, String status) {
    }

    public record BaselineDetail(String batchRef, LocalDateTime cutoffTime, List<Evidence> items,
                                 List<String> approvedItemKeys, String status) {
    }

    public record AuthorizationCoverage(String itemKey, String name, String authorizationStatus) {
    }

    public record FeeAuthorizationProjection(String assessmentStatus, List<AuthorizationCoverage> methods) {
    }

    public record KnownCapability(String paymentType, String paymentMethod, String currency) {
    }

    public record AccessSummary(String assessmentStatus, LocalDateTime assessedAt, Long activeBindingCount,
                                Integer authorizedCount, Integer legacyCoverageCount, Integer configurationReadyCount,
                                Integer pendingConfigurationCount, Integer restrictedCount, Integer executionReadyCount,
                                List<KnownCapability> capabilities) {
        public static AccessSummary unavailable(String status) {
            return new AccessSummary(status, null, null, null, null, null, null, null, null, List.of());
        }
    }

    public record MethodRow(String itemKey, String name, String authorizationStatus, String authorizationVersion,
                            LocalDateTime effectiveTime, LocalDateTime expireTime, String applicationStatus,
                            String applicationItemId, String applicationVersion, String businessReadiness,
                            List<String> reasonCodes, FeeReference feeReference, boolean catalogEnabled,
                            boolean requestable) {
    }

    public record FeeReference(String versionId, String versionNo, LocalDateTime effectiveTime) {
    }

    public record ItemRow(String id, String itemKey, String status, int roundNo, String version, String source,
                          String operation, String reason, LocalDateTime submittedAt, String reviewReason,
                          List<HistoryRow> history) {
    }

    public record ApplicationRow(String applicationNo, String merchantId, List<ItemRow> items) {
    }

    public record BatchReceipt(String applicationNo, int itemCount) {
    }

    public record ItemReceipt(String applicationNo, String itemId, String itemKey, String status, String version,
                              String authorizationVersion, String reviewRecordId) {
    }

    public record HistoryRow(String id, String action, String fromStatus, String toStatus, String reason,
                             Integer roundNo, LocalDateTime occurredAt) {
    }
}
