package com.scott.payment.payment.service.capability;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.MerchantFeeVersionSnapshotService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelRouteService;
import com.scott.payment.payment.service.impl.MerchantRouteProfileCacheReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 运行真实准入保护 SQL。输入仅允许指向专用本地合成库的临时端口。 */
@EnabledIfEnvironmentVariable(named = "REQ001_FIXTURE_MYSQL_PORT", matches = "[0-9]{1,5}")
class PaymentAdmissionExecutionGuardMySqlTest {
    private static final String MERCHANT = "FIXTURE-GUARD-RACE";

    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private PaymentAdmissionExecutionGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void connectToIsolatedFixture() {
        int port = Integer.parseInt(System.getenv("REQ001_FIXTURE_MYSQL_PORT"));
        assertThat(port).isBetween(1024, 65535).isNotEqualTo(3306);
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port
                + "/req001_fixture?sslMode=DISABLED&connectTimeout=5000&socketTimeout=15000", "root", "");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("req001_fixture");
        assertThat(jdbc.queryForObject("SELECT merchant_name FROM base_merchant_info WHERE merchant_id = ?",
                String.class, MERCHANT)).isEqualTo("Synthetic Guard Race");

        var routing = mock(DynamicRoutingDataSource.class);
        when(routing.getDataSource(DataSourceName.MASTER)).thenReturn(source);
        ObjectProvider<DynamicRoutingDataSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(routing);
        var feeService = mock(MerchantFeeVersionSnapshotService.class);
        var snapshot = mock(FeeVersionSnapshot.class);
        when(snapshot.feePlanVersionId()).thenReturn(990002L);
        when(feeService.freezeActiveVersion(eq(MERCHANT), any(LocalDateTime.class)))
                .thenReturn(new FrozenMerchantFeeVersionSnapshotDTO(snapshot, "{}"));
        guard = new PaymentAdmissionExecutionGuard(provider, mock(MerchantRouteProfileCacheReader.class),
                mock(DefaultPaymentChannelRouteService.class), feeService, true);
        jdbc.update("DELETE FROM req001_guard_receipt WHERE request_key IN ('before-close', 'after-expiry')");
        jdbc.update("""
                UPDATE merchant_payment_authorization
                SET authorization_status = 'ACTIVE', expire_time = NULL
                WHERE merchant_id = ? AND item_key = 'CARD_BRAND:VISA'
                """, MERCHANT);
    }

    @Test
    void protectedCheckHoldsConfigurationUntilTheSeparateWriteCommits() throws Exception {
        var enteredPreparation = new CountDownLatch(1);
        var writerEntered = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var threads = Executors.newFixedThreadPool(2);
        try {
            var command = cardCommand();
            var payment = threads.submit(() -> guard.protect(command, null, "PAYMENT", () -> {
                enteredPreparation.countDown();
                await(allowCommit);
                return transaction.execute(status -> insertReceipt(command, "before-close"));
            }));
            await(enteredPreparation);
            assertThatThrownBy(() -> guard.precheck(cardCommand()))
                    .isInstanceOfSatisfying(ServiceException.class,
                            error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.NETWORK_BUSY.getCode()));
            var change = threads.submit(() -> transaction.execute(status -> {
                writerEntered.countDown();
                return suspend();
            }));
            await(writerEntered);
            assertThatThrownBy(() -> change.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            allowCommit.countDown();
            assertThat(payment.get(8, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(change.get(8, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM req001_guard_receipt WHERE request_key = 'before-close'",
                    Integer.class)).isEqualTo(1);
            expectAuthorizationRejection();
        } finally {
            allowCommit.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    void checkAfterCommittedClosureNeverEntersTheWriteTransaction() {
        Integer changed = transaction.execute(status -> suspend());
        assertThat(changed).isEqualTo(1);
        assertThatThrownBy(() -> guard.precheck(cardCommand()))
                .isInstanceOfSatisfying(PaymentAdmissionRejectedException.class,
                        error -> assertThat(error.reason())
                                .isEqualTo(PaymentAdmissionRejectedException.Reason.AUTHORIZATION_UNAVAILABLE));
        expectAuthorizationRejection();
    }

    @Test
    void checkBeforeExpiryMayCommitAfterExpiryAndLaterChecksReject() {
        jdbc.update("""
                UPDATE merchant_payment_authorization SET expire_time = CURRENT_TIMESTAMP(3) + INTERVAL 3 SECOND
                WHERE merchant_id = ? AND item_key = 'CARD_BRAND:VISA'
                """, MERCHANT);
        LocalDateTime expiry = jdbc.queryForObject("""
                SELECT expire_time FROM merchant_payment_authorization WHERE merchant_id = ? AND item_key = 'CARD_BRAND:VISA'
                """, LocalDateTime.class, MERCHANT);
        var command = cardCommand();
        Integer written = guard.protect(command, null, "PAYMENT", () -> transaction.execute(status -> {
            jdbc.queryForObject("""
                    SELECT SLEEP(GREATEST(0, TIMESTAMPDIFF(MICROSECOND, CURRENT_TIMESTAMP(3), ?)) / 1000000.0 + 0.02)
                    """, Integer.class, expiry);
            return insertReceipt(command, "after-expiry");
        }));
        assertThat(written).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT checked_at FROM req001_guard_receipt WHERE request_key = 'after-expiry'",
                LocalDateTime.class)).isBefore(expiry);
        assertThat(jdbc.queryForObject("SELECT admitted_at FROM req001_guard_receipt WHERE request_key = 'after-expiry'",
                LocalDateTime.class)).isAfterOrEqualTo(expiry);
        expectAuthorizationRejection();
    }

    private int suspend() {
        jdbc.update("UPDATE base_merchant_info SET payment_authorization_revision = payment_authorization_revision + 1 WHERE merchant_id = ?",
                MERCHANT);
        return jdbc.update("""
                UPDATE merchant_payment_authorization SET authorization_status = 'SUSPENDED', version = version + 1
                WHERE merchant_id = ? AND item_key = 'CARD_BRAND:VISA'
                """, MERCHANT);
    }

    private int insertReceipt(PaymentCreateCommandDTO command, String requestKey) {
        var proof = JsonUtils.parseObject(command.getPaymentAdmissionSnapshotJson(), ProofTimestamp.class);
        return jdbc.update("""
                INSERT INTO req001_guard_receipt (request_key, checked_at, admitted_at)
                VALUES (?, ?, CURRENT_TIMESTAMP(3))
                """, requestKey, proof.checkedAt());
    }

    private void expectAuthorizationRejection() {
        assertThatThrownBy(() -> guard.protect(cardCommand(), null, "PAYMENT", () -> {
            throw new AssertionError("rejected preparation entered the separate transaction");
        })).isInstanceOfSatisfying(PaymentAdmissionRejectedException.class, error ->
                assertThat(error.reason()).isEqualTo(PaymentAdmissionRejectedException.Reason.AUTHORIZATION_UNAVAILABLE));
    }

    private static PaymentCreateCommandDTO cardCommand() {
        var command = new PaymentCreateCommandDTO();
        command.setMerchantId(MERCHANT);
        command.setPaymentMethod("BANK_CARD");
        var card = new PaymentCreateCommandDTO.TransactionInfoDTO();
        card.setCardBrand("VISA");
        command.setTransactionInfo(card);
        return command;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private record ProofTimestamp(LocalDateTime checkedAt) {
    }
}
