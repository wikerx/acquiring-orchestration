package com.scott.payment.admin.dto.risk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 拒付预警配置请求和响应。凭据原文只存在于独立写入请求。 */
public final class DisputeAlertDtos {

    private DisputeAlertDtos() {
    }

    public enum Product { ETHOCA, RDR, CDRN, AMEX_INQUIRY }

    public enum PlatformType { AGGREGATOR, ACQUIRER_PSP, NETWORK_DIRECT }

    public enum Environment { TEST, PRODUCTION }

    public enum Status { DRAFT, ENABLED, DISABLED }

    public enum Coverage { PENDING, CONFIRMED }

    public enum Enrollment { PENDING, CONFIRMED, REVOKED }

    public enum Transport { HTTPS, SFTP_PULL, SFTP_DROP }

    public enum IdentityType { ACQUIRER_MID, BIN_CAID }

    public record PlatformProductInput(@NotNull Product product, @NotNull Coverage coverage,
                                       @Size(max = 16) List<@NotBlank @Size(max = 32) String> cardBrands,
                                       @NotNull Boolean enabled) {
    }

    public record PlatformInput(@Size(max = 32) String code, @NotBlank @Size(max = 80) String name,
                                @NotNull PlatformType type, @NotNull Boolean enabled,
                                @Size(max = 250) String evidence,
                                @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid PlatformProductInput> products,
                                @Positive Long version) {
    }

    public record CardBrandOption(String value, String label, String extraJson) {
    }

    public record PlatformProductView(long id, Product product, Coverage coverage, List<String> cardBrands,
                                      boolean enabled) {
    }

    public record PlatformView(long id, String code, String name, PlatformType type, boolean enabled,
                               String evidence, String logoVersion, long version, LocalDateTime updateTime, List<PlatformProductView> products) {
    }

    public record Delivery(Transport transport, @Size(max = 120) String host, @Size(max = 120) String directory,
                           @Size(max = 120) String schedule, @Size(max = 80) String sourceAuthentication,
                           @Size(max = 250) String outcomeUrl) {
    }

    public record AccountProductInput(@NotNull Product product, @NotNull Status status, @Valid Delivery delivery) {
    }

    public record AccountCreate(@NotNull @Positive Long platformId,
                                @NotBlank @Size(max = 80) String merchantNumber, @NotNull Status status,
                                @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid AccountProductInput> products) {
    }

    public record AccountUpdate(@NotBlank @Size(max = 80) String merchantNumber, @NotNull Status status,
                                @NotNull @Positive Long version) {
    }

    public record AccountProductUpdate(@NotNull Status status, @Valid Delivery delivery, @NotNull @Positive Long version) {
    }

    public record CredentialUpdate(@NotNull @Positive Long version, @Size(max = 16384) String publicKey,
                                   @Size(max = 16384) String privateKey) {
        @Override
        public String toString() {
            return "CredentialUpdate[version=" + version + "]";
        }
    }

    public record CredentialSummary(boolean publicKeyRegistered, boolean privateKeyRegistered,
                                    String publicKeyFingerprint, Long version, LocalDateTime registeredTime) {
    }

    public record ProviderProductInput(@NotNull Product product, @NotNull Status status) {
    }

    public record ProviderAccountWrite(@Positive Long platformId, @Positive Long version,
                                       @NotBlank @Size(max = 80) String merchantNumber, @NotNull Status status,
                                       @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid ProviderProductInput> products,
                                       @Size(max = 20) Map<String, Object> settings,
                                       @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 6)
                                       Map<String, String> credentials) {
        @Override
        public String toString() {
            return "ProviderAccountWrite[version=" + version + "]";
        }
    }

    public record ProviderConfigView(Map<String, Object> values, List<String> registered, boolean activationValidated) {
    }

    public record AccountProductView(long id, Product product, Status status, Delivery delivery, boolean available) {
    }

    public record AccountView(long id, long platformId, String platformCode, String platformName,
                              String merchantNumber, Status status, long version, LocalDateTime updateTime,
                              CredentialSummary credentials, List<AccountProductView> products, ProviderConfigView providerConfig) {
    }

    public record IdentityInput(@NotNull IdentityType type, @NotBlank @Size(max = 100) String acquirer,
                                @Size(max = 128) String acquirerMid, @Size(max = 8) String acquirerBin,
                                @Size(max = 80) String caid, @NotNull Boolean enabled) {
    }

    public record DescriptorInput(@NotBlank @Size(max = 128) String value, @NotNull Boolean enabled) {
    }

    public record BindingInput(@NotNull @Positive Long midConfigId, @NotNull @Positive Long accountProductId,
                               @Size(max = 80) String externalMerchantId, @Size(max = 80) String upstreamMerchantId,
                               @NotNull Enrollment enrollment, @Size(max = 180) String enrollmentEvidence,
                               @NotNull @Size(max = 100) List<@NotNull @Valid IdentityInput> identifiers,
                               @NotNull @Size(max = 100) List<@NotNull @Valid DescriptorInput> descriptors,
                               @Positive Long version) {
    }

    public record BindingStatusUpdate(@NotNull Status status, @NotNull @Positive Long version) {
    }

    public record MidOption(long id, String channelCode, String channelMid, String midName) {
    }

    public record SourceOption(long accountProductId, long accountId, long platformId, String platformName,
                               String merchantNumber, Product product) {
    }

    public record BindingSummary(long id, MidOption mid, SourceOption source, String externalMerchantId,
                                 String upstreamMerchantId, Enrollment enrollment, String enrollmentEvidence,
                                 Status status, boolean sourceAvailable, boolean effective, int identifierCount,
                                 int descriptorCount, long version, LocalDateTime updateTime) {
    }

    public record BindingDetail(BindingSummary binding, List<IdentityInput> identifiers, List<DescriptorInput> descriptors,
                                List<MidOption> overlappingMids) {
    }
}
