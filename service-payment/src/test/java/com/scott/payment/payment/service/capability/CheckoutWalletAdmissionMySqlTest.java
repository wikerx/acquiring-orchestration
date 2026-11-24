package com.scott.payment.payment.service.capability;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.service.MerchantFeeVersionSnapshotService;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelRouteService;
import com.scott.payment.payment.service.impl.MerchantRouteProfileCacheReader;
import com.scott.payment.payment.service.impl.PaymentChannelMidMetadataCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 在独立回环库通过真实准入门禁，钱包组件和渠道仅使用测试替身。 */
@EnabledIfEnvironmentVariable(named = "REQ001_WALLET_FIXTURE_PASSWORD", matches = ".+")
class CheckoutWalletAdmissionMySqlTest {

    private static final String MERCHANT = "200046";
    private static final String APPLE_PAY = "APPLE_PAY";
    private static final String VISA_KEY = "CARD_BRAND:VISA";
    private static final String WALLET_KEY = "METHOD:APPLE_PAY";

    private JdbcTemplate jdbc;
    private Long mockMidId;
    private Map<String, String> originalGrants;
    private PaymentAdmissionExecutionGuard guard;
    private PaymentChannelClient mockChannel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void connectToIsolatedWalletFixture() {
        var source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:24316/req001_triad?sslMode=DISABLED&allowPublicKeyRetrieval=true"
                        + "&connectTimeout=5000&socketTimeout=10000",
                "root", System.getenv("REQ001_WALLET_FIXTURE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("req001_triad");
        assertThat(jdbc.queryForObject("""
                SELECT payment_admission_mode FROM base_merchant_info WHERE merchant_id = ?
                """, String.class, MERCHANT)).isEqualTo("ENFORCED");
        RouteOption wallet = loadMockWalletRoute();
        mockMidId = wallet.getMidConfigId();

        var routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource(DataSourceName.MASTER)).thenReturn(source);
        ObjectProvider<DynamicRoutingDataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(routing);
        var profile = new MerchantRouteProfile();
        profile.setMerchantId(MERCHANT);
        profile.setRouteOptions(List.of(wallet));
        var reader = mock(MerchantRouteProfileCacheReader.class);
        when(reader.findFresh(MERCHANT)).thenReturn(profile);
        var router = new DefaultPaymentChannelRouteService(mock(PaymentChannelMidConfigMapper.class),
                mock(PaymentChannelInfoMapper.class), mock(MerchantRouteProfileCacheService.class),
                mock(PaymentChannelMidMetadataCache.class));
        var fee = mock(FeeVersionSnapshot.class);
        Long feeVersion = jdbc.queryForObject("""
                SELECT current_version_id FROM fee_plan
                WHERE merchant_id = ? AND plan_type = 'MERCHANT' AND status = 'ENABLED' AND deleted = 0
                """, Long.class, MERCHANT);
        when(fee.feePlanVersionId()).thenReturn(feeVersion);
        var fees = mock(MerchantFeeVersionSnapshotService.class);
        when(fees.freezeActiveVersion(eq(MERCHANT), any(LocalDateTime.class)))
                .thenReturn(new FrozenMerchantFeeVersionSnapshotDTO(fee, "{}"));
        guard = new PaymentAdmissionExecutionGuard(provider, reader, router, fees, true);
        mockChannel = mock(PaymentChannelClient.class);
        originalGrants = Map.of(VISA_KEY, grant(VISA_KEY), WALLET_KEY, grant(WALLET_KEY));
    }

    @AfterEach
    void restoreWalletFixture() {
        if (originalGrants != null) {
            originalGrants.forEach(this::setGrant);
        }
    }

    @Test
    void availableComponentCannotInvokeChannelWhenWalletGrantIsSuspended() {
        setGrant(VISA_KEY, "ACTIVE");
        setGrant(WALLET_KEY, "SUSPENDED");

        expectNoGrant(APPLE_PAY, WALLET_KEY, null);

        verifyNoInteractions(mockChannel);
    }

    @Test
    void availableComponentCannotInvokeChannelWhenWalletHasNeverBeenGranted() {
        String heldKey = "METHOD:REQ001_WALLET_HELD";
        setGrant(VISA_KEY, "ACTIVE");
        moveWalletGrant(WALLET_KEY, heldKey);
        try {
            expectNoGrant(APPLE_PAY, WALLET_KEY, null);
            verifyNoInteractions(mockChannel);
        } finally {
            moveWalletGrant(heldKey, WALLET_KEY);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "VISA")
    void walletWithVisaOrNoChannelBrandNeverGrantsDirectVisa(String channelBrand) {
        setGrant(VISA_KEY, "SUSPENDED");
        setGrant(WALLET_KEY, "ACTIVE");
        when(mockChannel.payment(any())).thenAnswer(invocation -> {
            ChannelPaymentRequest request = invocation.getArgument(0);
            assertThat(request.getPaymentMethod()).isEqualTo(APPLE_PAY);
            assertThat(request.getCardBrand()).isNull();
            var summary = new ChannelPaymentResponse.PaymentMethodSummary();
            summary.setPaymentMethod(APPLE_PAY);
            summary.setPaymentBrand(channelBrand);
            var response = new ChannelPaymentResponse();
            response.setPaymentMethodSummary(summary);
            return response;
        });

        ChannelPaymentResponse.PaymentMethodSummary wallet = submitSimulatedWallet().getPaymentMethodSummary();

        assertThat(wallet.getPaymentMethod()).isEqualTo(APPLE_PAY);
        assertThat(wallet.getPaymentBrand()).isEqualTo(channelBrand);
        expectNoGrant("BANK_CARD", VISA_KEY, "VISA");
        verify(mockChannel).payment(any());
    }

    private ChannelPaymentResponse submitSimulatedWallet() {
        var command = paymentCommand(APPLE_PAY, null);
        guard.precheck(command);
        var route = guard.planRoute(command);
        assertThat(route.getMidConfigId()).isEqualTo(mockMidId);
        var proof = guard.protect(command, route, "PAYMENT", () ->
                JsonUtils.parseObject(command.getPaymentAdmissionSnapshotJson(), AdmissionProof.class));
        assertThat(proof.itemKey()).isEqualTo(WALLET_KEY);
        var request = new ChannelPaymentRequest();
        request.setPaymentMethod(command.getPaymentMethod());
        request.setCardBrand(command.getTransactionInfo().getCardBrand());
        return mockChannel.payment(request);
    }

    private void expectNoGrant(String method, String key, String brand) {
        assertThatThrownBy(() -> guard.precheck(paymentCommand(method, brand)))
                .isInstanceOfSatisfying(PaymentAdmissionRejectedException.class, rejected -> {
                    assertThat(rejected.itemKey()).isEqualTo(key);
                    assertThat(rejected.reason())
                            .isEqualTo(PaymentAdmissionRejectedException.Reason.AUTHORIZATION_UNAVAILABLE);
                });
    }

    private RouteOption loadMockWalletRoute() {
        return jdbc.queryForObject("""
                SELECT b.id AS binding_id, b.binding_status, mid.id AS mid_id, mid.mid_status,
                       mid.channel_mid, mid.payment_method_scope, mid.transaction_type_scope,
                       mid.allowed_country_scope, mid.currency_scope,
                       ch.id AS channel_id, ch.channel_status, ch.support_acquiring,
                       cap.id AS capability_id, cap.capability_status, cap.sort_order, cap.transaction_type,
                       curr.currency_code
                FROM merchant_channel_mid_binding b
                JOIN channel_mid_config mid ON mid.id = b.mid_config_id AND mid.deleted = 0
                JOIN channel_info ch ON ch.id = mid.channel_id AND ch.deleted = 0
                JOIN channel_payment_capability cap ON cap.channel_id = ch.id AND cap.deleted = 0
                JOIN channel_capability_currency curr ON curr.capability_id = cap.id AND curr.deleted = 0
                WHERE b.merchant_id = ? AND b.deleted = 0 AND mid.channel_mid = 'REQ001-PHY-MID'
                  AND cap.payment_method = 'APPLE_PAY' AND curr.currency_status = 1 AND curr.currency_code = 'USD'
                """, (row, index) -> {
            var option = new RouteOption();
            option.setBindingId(row.getLong("binding_id"));
            option.setBindingStatus(row.getInt("binding_status"));
            option.setMidConfigId(row.getLong("mid_id"));
            option.setMidStatus(row.getInt("mid_status"));
            option.setChannelMid(row.getString("channel_mid"));
            option.setBusinessType("ACQUIRING");
            option.setPaymentMethodScope(row.getString("payment_method_scope"));
            option.setTransactionTypeScope(row.getString("transaction_type_scope"));
            option.setAllowedCountryScope(row.getString("allowed_country_scope"));
            option.setCurrencyScope(row.getString("currency_scope"));
            option.setChannelId(row.getLong("channel_id"));
            option.setChannelCode("MOCK_WALLET_CHANNEL");
            option.setChannelStatus(row.getInt("channel_status"));
            option.setSupportAcquiring(row.getInt("support_acquiring"));
            option.setCapabilityId(row.getLong("capability_id"));
            option.setCapabilityStatus(row.getInt("capability_status"));
            option.setCapabilitySortOrder(row.getInt("sort_order"));
            option.setCapabilityBusinessType("ACQUIRING");
            option.setCapabilityPaymentMethod(APPLE_PAY);
            option.setCapabilityTransactionType(row.getString("transaction_type"));
            option.setDefaultTransactionCurrency(row.getString("currency_code"));
            option.setSupportedCurrencies(List.of(row.getString("currency_code")));
            return option;
        }, MERCHANT);
    }

    private String grant(String key) {
        return jdbc.queryForObject("""
                SELECT authorization_status FROM merchant_payment_authorization WHERE merchant_id = ? AND item_key = ?
                """, String.class, MERCHANT, key);
    }

    private void setGrant(String key, String status) {
        assertThat(jdbc.update("""
                UPDATE merchant_payment_authorization SET authorization_status = ?
                WHERE merchant_id = ? AND item_key = ?
                """, status, MERCHANT, key)).isOne();
    }

    private void moveWalletGrant(String previousKey, String nextKey) {
        assertThat(jdbc.update("""
                UPDATE merchant_payment_authorization SET item_key = ?
                WHERE merchant_id = ? AND item_key = ?
                """, nextKey, MERCHANT, previousKey)).isOne();
    }

    private PaymentCreateCommandDTO paymentCommand(String method, String brand) {
        var command = new PaymentCreateCommandDTO();
        command.setMerchantId(MERCHANT);
        command.setMerchantOrderNo("REQ001-WALLET-SIMULATION");
        command.setPaymentMethod(method);
        command.setTransactionType("PAYMENT");
        command.setCurrency("USD");
        command.setAmount(new BigDecimal("7.00"));
        var info = new PaymentCreateCommandDTO.TransactionInfoDTO();
        info.setCardBrand(brand);
        command.setTransactionInfo(info);
        return command;
    }

    private record AdmissionProof(String itemKey) {
    }
}
