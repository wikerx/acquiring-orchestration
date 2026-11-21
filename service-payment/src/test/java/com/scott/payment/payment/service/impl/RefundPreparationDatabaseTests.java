package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.config.RefundManagementProperties;
import com.scott.payment.payment.domain.state.DefaultTransactionStateMachineService;
import com.scott.payment.payment.entity.*;
import com.scott.payment.payment.service.*;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 在独立 H2 数据库调用真实退款准备服务，验证真实行锁、唯一键与事务回滚，不连接业务库或渠道。 */
class RefundPreparationDatabaseTests {
    private static final LocalDateTime ROOT_TIME = LocalDateTime.of(2026, 11, 20, 8, 0);
    private JdbcTemplate jdbc;
    private DefaultRefundTransactionPreparationService service;
    private DefaultCaptureTransactionPreparationService captureService;
    private final AtomicBoolean failOutbox = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE root_order (id VARCHAR(32) PRIMARY KEY, body VARCHAR(20000))");
        jdbc.execute("CREATE TABLE action (id VARCHAR(64) PRIMARY KEY, body VARCHAR(20000))");
        jdbc.execute("CREATE TABLE idem (id VARCHAR(100) PRIMARY KEY, body VARCHAR(20000))");
        jdbc.execute("CREATE TABLE outbox (id VARCHAR(64) PRIMARY KEY, body VARCHAR(20000))");
        jdbc.update("INSERT INTO root_order VALUES (?, ?)", "ROOT", JsonUtils.toJsonString(root()));
        jdbc.update("INSERT INTO action VALUES (?, ?)", "CAPTURE-1", JsonUtils.toJsonString(source()));
        TransactionRecordService records = mock(TransactionRecordService.class);
        when(records.findSourceOrderByTransactionId(anyString(), any(), any())).thenAnswer(invocation -> readRoot(false));
        when(records.findSourceOperationByTransactionId(anyString(), any())).thenAnswer(invocation -> actions().stream()
                .filter(action -> action.getTransactionId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(records.lockOrder(any(), anyString())).thenAnswer(invocation -> readRoot(true));
        when(records.findOperationsByOperationId(anyString(), any(), any())).thenAnswer(invocation -> actions());
        when(records.findNonTerminalRefunds(anyString(), anyString(), any(), any())).thenAnswer(invocation ->
                actions().stream().filter(action -> "REFUND".equals(action.getTransactionType())
                        && List.of("PROCESSING", "PENDING").contains(action.getTransactionStatus())).toList());
        when(records.findNonTerminalCaptures(anyString(), anyString(), anyString(), any(), any())).thenAnswer(invocation ->
                actions().stream().filter(action -> List.of("CAPTURE", "PRE_AUTH_COMPLETION").contains(action.getTransactionType())
                        && List.of("PROCESSING", "PENDING").contains(action.getTransactionStatus())).toList());
        doAnswer(invocation -> {
            TransactionFollowUpRecordDTO fact = invocation.getArgument(0);
            TransactionOperationDO action = new TransactionOperationDO();
            action.setTransactionId(fact.getResultDTO().getTransactionId());
            action.setOperationId("ROOT"); action.setSourceTransactionId(fact.getCommandDTO().getTransactionInfo().getSourceTransactionId());
            action.setTransactionType(fact.getResultDTO().getTransactionType()); action.setTransactionStatus(fact.getResultDTO().getStatus());
            action.setLabelAmount(fact.getCommandDTO().getLabelAmount());
            action.setTransactionAmount(fact.getCommandDTO().getTransactionAmount());
            jdbc.update("INSERT INTO action VALUES (?, ?)", action.getTransactionId(), JsonUtils.toJsonString(action));
            return null;
        }).when(records).recordFollowUpTransaction(any());
        TransactionIdempotencyService idempotency = mock(TransactionIdempotencyService.class);
        when(idempotency.find(anyString(), anyString())).thenAnswer(invocation -> findIdempotency(invocation.getArgument(1)));
        when(idempotency.newProcessingRecord(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    TransactionIdempotencyDO value = new TransactionIdempotencyDO();
                    value.setIdempotencyScope(invocation.getArgument(0)); value.setIdempotencyKey(invocation.getArgument(1));
                    value.setMerchantId(invocation.getArgument(2)); value.setMerchantOrderId(invocation.getArgument(4));
                    value.setTransactionDateTime(invocation.getArgument(6)); value.setRequestFingerprint(invocation.getArgument(8));
                    value.setTransactionStatus("PROCESSING");
                    return value;
                });
        when(idempotency.tryBegin(any())).thenAnswer(invocation -> {
            TransactionIdempotencyDO value = invocation.getArgument(0);
            try { jdbc.update("INSERT INTO idem VALUES (?, ?)", value.getIdempotencyKey(), JsonUtils.toJsonString(value)); return true; }
            catch (DuplicateKeyException exception) { return false; }
        });
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            TransactionIdempotencyDO value = findIdempotency(key).orElseThrow();
            value.setOperationId(invocation.getArgument(2)); value.setTransactionId(invocation.getArgument(3));
            value.setTransactionStatus(invocation.getArgument(4)); value.setResultSnapshot(invocation.getArgument(7));
            jdbc.update("UPDATE idem SET body = ? WHERE id = ?", JsonUtils.toJsonString(value), key);
            return null;
        }).when(idempotency).complete(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
        TransactionEventOutboxService outbox = mock(TransactionEventOutboxService.class);
        doAnswer(invocation -> {
            TransactionEventOutboxDO event = invocation.getArgument(0);
            jdbc.update("INSERT INTO outbox VALUES (?, ?)", event.getEventNo(), JsonUtils.toJsonString(event));
            if (failOutbox.get()) throw new IllegalStateException("injected outbox failure");
            return null;
        }).when(outbox).save(any());
        IsoDictionaryService iso = mock(IsoDictionaryService.class);
        when(iso.getCurrency(anyString())).thenAnswer(invocation -> IsoCurrencyResolver.resolve(invocation.getArgument(0)));
        PaymentChannelRouteService routes = mock(PaymentChannelRouteService.class);
        var route = PaymentRouteResultDTO.routed("SCOTT_CHANNEL_A"); route.setMidConfigId(10L);
        when(routes.restore(anyString(), any(), any(), any())).thenReturn(route);
        service = new DefaultRefundTransactionPreparationService(iso, routes, idempotency, outbox, records,
                new DefaultTransactionStateMachineService(), new RefundScopeService(),
                new RefundApprovalPolicyService(new RefundManagementProperties()), null,
                new DataSourceTransactionManager(dataSource));
        captureService = new DefaultCaptureTransactionPreparationService(iso, routes, idempotency, outbox,
                mock(PaymentExchangeRateService.class), records, new DefaultTransactionStateMachineService(),
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void concurrentRequestsCannotReserveMoreThanOneCapture() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> reserveAfter(start, "RACE-1", "700");
            Callable<Boolean> second = () -> reserveAfter(start, "RACE-2", "700");
            Future<Boolean> a = pool.submit(first); Future<Boolean> b = pool.submit(second); start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(count("action")).isEqualTo(2); // 一个来源 + 一笔退款
            assertThat(count("idem")).isEqualTo(1);
            assertThat(count("outbox")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void duplicateReusesIdentityAndPendingApprovalDoesNotBlockUnreservedRemainder() {
        var first = service.prepareRefund(command("R1", "300"), "R1");
        var duplicate = service.prepareRefund(command("R1", "300"), "R1");
        assertThat(duplicate.isCallChannel()).isFalse();
        assertThat(duplicate.getResultDTO().getTransactionId()).isEqualTo(first.getResultDTO().getTransactionId());
        TransactionOperationDO reserved = actions().stream().filter(action -> "REFUND".equals(action.getTransactionType())).findFirst().orElseThrow();
        reserved.setTransactionStatus("PENDING"); // 与真实待审批相同的动作预占状态
        jdbc.update("UPDATE action SET body = ? WHERE id = ?", JsonUtils.toJsonString(reserved), reserved.getTransactionId());
        assertThat(service.prepareRefund(command("R2", "700"), "R2").isCallChannel()).isTrue();
        assertThatThrownBy(() -> service.prepareRefund(command("R3", "0.01"), "R3")).isInstanceOf(ServiceException.class);
        assertThat(count("outbox")).isEqualTo(2);
    }

    @Test
    void outboxFailureRollsBackActionReservationAndIdempotencyTogether() {
        failOutbox.set(true);
        assertThatThrownBy(() -> service.prepareRefund(command("ROLLBACK", "1000"), "ROLLBACK"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("action")).isEqualTo(1);
        assertThat(count("idem")).isZero(); assertThat(count("outbox")).isZero();
        assertThat(readRoot(false).getAvailableRefundAmount()).isEqualByComparingTo("1000");
        failOutbox.set(false);
        assertThat(service.prepareRefund(command("ROLLBACK", "1000"), "ROLLBACK").isCallChannel()).isTrue();
    }

    @Test
    void successfulPartialCompletionClosesLifecycleEvenWhenOldProjectionStillHasRemainder() {
        TransactionOrderDO pre = root();
        pre.setTransactionType("PRE_AUTHORIZATION"); pre.setLabelAmount(new BigDecimal("1200"));
        pre.setTransactionAmount(new BigDecimal("1200")); pre.setAuthorizedAmount(new BigDecimal("1200"));
        pre.setLabelAuthorizedAmount(new BigDecimal("1200")); pre.setAvailableCaptureAmount(new BigDecimal("1200"));
        pre.setLabelAvailableCaptureAmount(new BigDecimal("1200"));
        jdbc.update("UPDATE root_order SET body = ? WHERE id = 'ROOT'", JsonUtils.toJsonString(pre));
        TransactionOperationDO origin = source(); origin.setTransactionType("PRE_AUTHORIZATION");
        jdbc.update("UPDATE action SET body = ? WHERE id = 'CAPTURE-1'", JsonUtils.toJsonString(origin));
        var first = captureService.prepareCapture(command("COMPLETE-1", "1000"), "COMPLETE-1",
                com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION);
        assertThat(first.isCallChannel()).isTrue();
        assertThatThrownBy(() -> captureService.prepareCapture(command("COMPLETE-2", "100"), "COMPLETE-2",
                com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION))
                .isInstanceOf(ServiceException.class);
        TransactionOperationDO completed = actions().stream().filter(action -> "PRE_AUTH_COMPLETION".equals(action.getTransactionType())).findFirst().orElseThrow();
        completed.setTransactionStatus("SUCCESS");
        jdbc.update("UPDATE action SET body = ? WHERE id = ?", JsonUtils.toJsonString(completed), completed.getTransactionId());
        pre.setCapturedAmount(new BigDecimal("1000")); pre.setLabelCapturedAmount(new BigDecimal("1000"));
        pre.setAvailableCaptureAmount(new BigDecimal("200")); pre.setLabelAvailableCaptureAmount(new BigDecimal("200"));
        jdbc.update("UPDATE root_order SET body = ? WHERE id = 'ROOT'", JsonUtils.toJsonString(pre));
        assertThatThrownBy(() -> captureService.prepareCapture(command("COMPLETE-3", "100"), "COMPLETE-3",
                com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION))
                .isInstanceOf(ServiceException.class).hasMessageContaining("closed");
        assertThat(readRoot(false).getTransactionStatus()).isEqualTo("SUCCESS");
        assertThat(count("action")).isEqualTo(2); assertThat(count("outbox")).isEqualTo(1);
    }

    private boolean reserveAfter(CountDownLatch start, String id, String amount) throws InterruptedException {
        start.await();
        try { service.prepareRefund(command(id, amount), id); return true; }
        catch (ServiceException exception) { return false; }
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private Optional<TransactionIdempotencyDO> findIdempotency(String id) {
        return jdbc.query("SELECT body FROM idem WHERE id = ?", (rs, row) -> JsonUtils.parseObject(rs.getString(1), TransactionIdempotencyDO.class), id).stream().findFirst();
    }
    private TransactionOrderDO readRoot(boolean lock) {
        return jdbc.queryForObject("SELECT body FROM root_order WHERE id = 'ROOT'" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> JsonUtils.parseObject(rs.getString(1), TransactionOrderDO.class));
    }
    private List<TransactionOperationDO> actions() {
        return jdbc.query("SELECT body FROM action", (rs, row) -> JsonUtils.parseObject(rs.getString(1), TransactionOperationDO.class));
    }
    private TransactionOrderDO root() {
        TransactionOrderDO root = new TransactionOrderDO();
        root.setOperationId("ROOT"); root.setRootTransactionId("AUTH-1"); root.setMerchantId("M-QA"); root.setMerchantOrderNo("ORDER-QA");
        root.setTransactionType("AUTHORIZATION"); root.setTransactionStatus("SUCCESS"); root.setTransactionDateTime(ROOT_TIME);
        root.setLabelCurrency("USD"); root.setTransactionCurrency("USD"); root.setLabelCurrencyExponent(2);
        root.setLabelAmount(new BigDecimal("1000")); root.setTransactionAmount(new BigDecimal("1000")); root.setTransactionRate(BigDecimal.ONE);
        root.setAvailableRefundAmount(new BigDecimal("1000")); root.setLabelAvailableRefundAmount(new BigDecimal("1000"));
        root.setChannelCode("SCOTT_CHANNEL_A"); root.setChannelMidConfigId(10L);
        return root;
    }
    private TransactionOperationDO source() {
        TransactionOperationDO source = new TransactionOperationDO(); source.setOperationId("ROOT"); source.setTransactionId("CAPTURE-1");
        source.setTransactionType("CAPTURE"); source.setTransactionStatus("SUCCESS"); source.setTransactionDateTime(ROOT_TIME);
        source.setLabelAmount(new BigDecimal("1000")); source.setTransactionAmount(new BigDecimal("1000"));
        source.setChannelCode("SCOTT_CHANNEL_A"); source.setChannelMidConfigId(10L); source.setChannelTransactionId("CHANNEL-CAPTURE-1");
        return source;
    }
    private PaymentCreateCommandDTO command(String id, String amount) {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO(); command.setMerchantId("M-QA"); command.setMerchantOrderNo("ORDER-QA");
        command.setMerchantOrderId(id); command.setTransactionDateTime(LocalDateTime.now()); command.setTransactionType("REFUND");
        command.setCurrency("USD"); command.setAmount(new BigDecimal(amount)); command.setPaymentMethod("BANK_CARD");
        var info = new PaymentCreateCommandDTO.TransactionInfoDTO(); info.setSourceTransactionId("CAPTURE-1");
        info.setSourceTransactionDateTime(ROOT_TIME); info.setRootTransactionDateTime(ROOT_TIME); command.setTransactionInfo(info);
        return command;
    }
}
