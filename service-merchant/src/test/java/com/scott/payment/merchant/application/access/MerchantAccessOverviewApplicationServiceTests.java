package com.scott.payment.merchant.application.access;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.merchant.application.merchantpayment.MerchantPaymentLinkedProjection;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.AccessSummary;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.KnownCapability;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.AccessCheckStatus;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.AccessRuleFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.FeeFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.MerchantProfileFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.PaymentCapabilityFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.SettlementFacts;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户接入概览状态计算测试，覆盖六阶段完成、商户补件、平台配置和技术缺口语义。
 */
class MerchantAccessOverviewApplicationServiceTests {

    private static final String MERCHANT_ID = "M10000001";
    private static final LocalDateTime UPDATED_TIME = LocalDateTime.of(2026, 11, 4, 10, 0);

    @Test
    void shouldReportReadyWhenAllSixStagesAreAvailable() {
        Fixture fixture = readyFixture();

        var result = fixture.service().queryOverview(" M10000001 ");

        assertThat(result.getOverallStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(6);
        assertThat(result.getRequiredCheckCount()).isEqualTo(6);
        assertThat(result.getMerchantProfile().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getSettlement().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getPaymentCapability().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getPaymentCapability().getCapabilities())
                .extracting("paymentType", "paymentMethod", "currency")
                .containsExactly(tuple("BANK_CARD", "VISA", "USD"));
        assertThat(result.getFee().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getRisk().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getTechnicalStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getSourceUrl().getStatus()).isEqualTo(AccessCheckStatus.READY);
        verify(fixture.keyMaterialService()).queryMaterial(MERCHANT_ID);
        verify(fixture.queryService()).countActivePaymentBindings(eq(MERCHANT_ID), any(LocalDateTime.class));
    }

    @Test
    void shouldRequireMerchantActionWhenOnboardingNeedsSupplement() {
        Fixture fixture = readyFixture();
        when(fixture.queryService().findMerchantProfile(MERCHANT_ID)).thenReturn(
                profile("SUPPLEMENT_REQUIRED", "SUPPLEMENT", "NOT_READY", 2));

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getOverallStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getMerchantProfile().getStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getRisk().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(4);
    }

    @Test
    void shouldReportPlatformProcessingWithoutExposingInternalRules() {
        Fixture fixture = readyFixture();
        when(fixture.queryService().findMerchantProfile(MERCHANT_ID)).thenReturn(
                profile("APPROVED", "PASSED", "NOT_READY", 3));
        when(fixture.queryService().findSettlement(MERCHANT_ID)).thenReturn(null);
        when(fixture.queryService().countActivePaymentBindings(eq(MERCHANT_ID), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(fixture.queryService().listPaymentCapabilities(eq(MERCHANT_ID), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(fixture.queryService().findFee(MERCHANT_ID)).thenReturn(null);

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getOverallStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getMerchantProfile().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getSettlement().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getPaymentCapability().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getFee().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getRisk().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getRisk().getRiskLevel()).isEqualTo(3);
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(2);
    }

    @Test
    void shouldRequireActionWhenTechnicalAccessIsIncomplete() {
        Fixture fixture = readyFixture();
        OpenApiMerchantKeyMaterialVO material = enabledMaterial();
        material.setMerchantResponsePrivateKeyAvailable(false);
        when(fixture.keyMaterialService().queryMaterial(MERCHANT_ID)).thenReturn(material);

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getOverallStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getTechnicalStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getCredentials().getStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getCredentials().getMerchantResponsePrivateKeyAvailable()).isFalse();
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(5);
    }

    @Test
    void shouldRequireActiveStoreWebsiteForTechnicalAccess() {
        Fixture fixture = readyFixture();
        when(fixture.queryService().summarizeSourceUrls(MERCHANT_ID))
                .thenReturn(facts(0, 0, 0, 0, 0));

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getOverallStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getTechnicalStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getSourceUrl().getRequired()).isTrue();
        assertThat(result.getSourceUrl().getStatus()).isEqualTo(AccessCheckStatus.ACTION_REQUIRED);
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(5);
    }

    @Test
    void shouldRejectUnknownAuthenticatedMerchantBeforeReadingSecurityMaterial() {
        OpenApiMerchantKeyMaterialService keyMaterialService = mock(OpenApiMerchantKeyMaterialService.class);
        MerchantAccessOverviewQueryService queryService = mock(MerchantAccessOverviewQueryService.class);
        when(queryService.findMerchantProfile(MERCHANT_ID)).thenReturn(null);
        MerchantAccessOverviewApplicationService service =
                new MerchantAccessOverviewApplicationService(keyMaterialService, queryService,
                        mock(MerchantPaymentLinkedProjection.class));

        assertThatThrownBy(() -> service.queryOverview(MERCHANT_ID))
                .isInstanceOf(ServiceException.class)
                .satisfies(error -> assertThat(((ServiceException) error).getCode())
                        .isEqualTo(ApiResultEnum.NOT_FOUND.getCode()))
                .hasMessageContaining("merchant not found");
        verify(keyMaterialService, never()).queryMaterial(MERCHANT_ID);
    }

    @Test
    void shouldNotCountOnlyWalletConfigurationAsExecutablePayment() {
        Fixture fixture = readyFixture();
        when(fixture.projection().overview(MERCHANT_ID)).thenReturn(
                new AccessSummary("EVALUATED", UPDATED_TIME, 1L, 1, 0, 1, 0, 0, 0, List.of()));

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getRequiredCheckCount()).isEqualTo(6);
        assertThat(result.getCompletedRequiredChecks()).isEqualTo(5);
        assertThat(result.getPaymentCapability().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        assertThat(result.getPaymentCapability().getConfigurationReadyCount()).isEqualTo(1);
        assertThat(result.getPaymentCapability().getExecutionReadyCount()).isZero();
        assertThat(result.getPaymentCapability().getCapabilities()).isEmpty();
        verify(fixture.queryService(), never()).listPaymentCapabilities(eq(MERCHANT_ID), any());
    }

    @Test
    void shouldKeepPaymentStageReadyWhenAtLeastOneAuthoritativeMethodIsExecutable() {
        Fixture fixture = readyFixture();
        when(fixture.projection().overview(MERCHANT_ID)).thenReturn(new AccessSummary(
                "EVALUATED", UPDATED_TIME, 1L, 2, 0, 1, 1, 0, 1,
                List.of(new KnownCapability("BANK_CARD", "VISA", "USD"))));

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getCompletedRequiredChecks()).isEqualTo(6);
        assertThat(result.getPaymentCapability().getStatus()).isEqualTo(AccessCheckStatus.READY);
        assertThat(result.getPaymentCapability().getCapabilities()).singleElement()
                .satisfies(item -> assertThat(item.getPaymentMethod()).isEqualTo("VISA"));
    }

    @Test
    void shouldKeepUnknownSeparateFromConfirmedZeroAndLegacyCompatibility() {
        Fixture fixture = readyFixture();
        when(fixture.projection().overview(MERCHANT_ID)).thenReturn(AccessSummary.unavailable("UNAVAILABLE"));

        var result = fixture.service().queryOverview(MERCHANT_ID);

        assertThat(result.getPaymentCapability().getAssessmentStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.getPaymentCapability().getAuthorizedCount()).isNull();
        assertThat(result.getPaymentCapability().getStatus()).isEqualTo(AccessCheckStatus.PENDING_REVIEW);
        verify(fixture.queryService(), never()).listPaymentCapabilities(eq(MERCHANT_ID), any());
    }

    /** 创建六阶段全部完成的真实事实组合，单个测试只覆盖需要变化的业务事实。 */
    private Fixture readyFixture() {
        OpenApiMerchantKeyMaterialService keyMaterialService = mock(OpenApiMerchantKeyMaterialService.class);
        MerchantAccessOverviewQueryService queryService = mock(MerchantAccessOverviewQueryService.class);
        when(queryService.findMerchantProfile(MERCHANT_ID)).thenReturn(
                profile("ACTIVE", "PASSED", "ACTIVE", 2));
        when(queryService.findSettlement(MERCHANT_ID)).thenReturn(new SettlementFacts(
                "USD", "Asia/Shanghai", "16:00:00", "AUTO",
                "T", 2, 2, "DAILY", null, UPDATED_TIME));
        when(queryService.countActivePaymentBindings(eq(MERCHANT_ID), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(queryService.listPaymentCapabilities(eq(MERCHANT_ID), any(LocalDateTime.class)))
                .thenReturn(List.of(new PaymentCapabilityFacts(
                        "BANK_CARD", "VISA", "USD", UPDATED_TIME)));
        when(queryService.findFee(MERCHANT_ID)).thenReturn(new FeeFacts(3, "ACTIVE", UPDATED_TIME));
        when(keyMaterialService.queryMaterial(MERCHANT_ID)).thenReturn(enabledMaterial());
        when(queryService.summarizeIpWhitelists(MERCHANT_ID)).thenReturn(facts(2, 2, 0, 0, 0));
        when(queryService.summarizeSourceUrls(MERCHANT_ID)).thenReturn(facts(1, 1, 0, 0, 0));
        MerchantPaymentLinkedProjection projection = mock(MerchantPaymentLinkedProjection.class);
        when(projection.overview(MERCHANT_ID)).thenReturn(AccessSummary.unavailable("HISTORY_PENDING"));
        return new Fixture(
                new MerchantAccessOverviewApplicationService(keyMaterialService, queryService, projection),
                keyMaterialService,
                queryService,
                projection);
    }

    private MerchantProfileFacts profile(
            String onboardingStatus,
            String reviewStatus,
            String activationStatus,
            Integer riskLevel) {
        return new MerchantProfileFacts(
                onboardingStatus, reviewStatus, activationStatus, "USD", riskLevel, UPDATED_TIME);
    }

    private OpenApiMerchantKeyMaterialVO enabledMaterial() {
        OpenApiMerchantKeyMaterialVO material = new OpenApiMerchantKeyMaterialVO();
        material.setOpenApiBaseUrl("https://openapi.example.test");
        material.setSdkVersion("1.0.0");
        material.setCryptoMode("RSA_AES_GCM");
        material.setJwtKeyStatus("ENABLED");
        material.setPlatformPayloadKeyStatus("ENABLED");
        material.setMerchantResponseKeyStatus("ENABLED");
        material.setMerchantResponsePrivateKeyAvailable(true);
        material.setJwtUpdatedTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        material.setPlatformPayloadUpdatedTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        material.setMerchantResponseUpdatedTime(LocalDateTime.of(2026, 11, 3, 10, 0));
        return material;
    }

    private AccessRuleFacts facts(long total, long active, long pending, long rejected, long disabled) {
        return new AccessRuleFacts(total, active, pending, rejected, disabled, UPDATED_TIME);
    }

    private record Fixture(
            MerchantAccessOverviewApplicationService service,
            OpenApiMerchantKeyMaterialService keyMaterialService,
            MerchantAccessOverviewQueryService queryService,
            MerchantPaymentLinkedProjection projection) {
    }
}
