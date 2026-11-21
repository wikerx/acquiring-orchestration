package com.scott.payment.payment.service.capability;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.scott.payment.capability.MerchantAdmissionPolicy;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.MerchantFeeVersionSnapshotService;
import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelRouteService;
import com.scott.payment.payment.service.impl.MerchantRouteProfileCacheReader;
import com.scott.payment.payment.service.capability.PaymentAdmissionRejectedException.Reason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 为交易准备取得主库配置共享锁。物理连接与原 TRANSACTION 短事务独立，不把主库连接绑定到 Spring 路由事务。
 */
@Slf4j
@Service
public class PaymentAdmissionExecutionGuard {

    private final ObjectProvider<DynamicRoutingDataSource> dataSourceProvider;
    private final MerchantRouteProfileCacheReader routeProfileReader;
    private final DefaultPaymentChannelRouteService router;
    private final MerchantFeeVersionSnapshotService fees;
    private final boolean enabled;
    private final Semaphore protectedPreparations;

    public PaymentAdmissionExecutionGuard(ObjectProvider<DynamicRoutingDataSource> dataSourceProvider,
                                           MerchantRouteProfileCacheReader routeProfileReader,
                                           DefaultPaymentChannelRouteService router,
                                           MerchantFeeVersionSnapshotService fees,
                                           boolean enabled) {
        this(dataSourceProvider, routeProfileReader, router, fees, enabled, 1);
    }

    @Autowired
    public PaymentAdmissionExecutionGuard(ObjectProvider<DynamicRoutingDataSource> dataSourceProvider,
                                           MerchantRouteProfileCacheReader routeProfileReader,
                                           DefaultPaymentChannelRouteService router,
                                           MerchantFeeVersionSnapshotService fees,
                                           @Value("${payment.admission.enforcement-enabled:false}") boolean enabled,
                                           @Value("${payment.admission.maximum-protected-preparations:1}") int maxProtected) {
        this.dataSourceProvider = dataSourceProvider;
        this.routeProfileReader = routeProfileReader;
        this.router = router;
        this.fees = fees;
        this.enabled = enabled;
        if (maxProtected < 1 || maxProtected > 128) {
            throw new IllegalArgumentException("payment admission maximum protected preparations is invalid");
        }
        this.protectedPreparations = new Semaphore(maxProtected, true);
    }

    /** 审批和迁移兼容版本可先部署，交易门禁由正式切换启用。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 初次计划从主库读取，最终提交前在配置保护下重新核对。 */
    public PaymentRouteResultDTO planRoute(PaymentCreateCommandDTO command) {
        return router.route(command, routeProfileReader.findFresh(command.getMerchantId()));
    }

    /** 快速检查只持有商户、方式和字典保护，返回前释放，避免无授权请求先预占限额。 */
    public void precheck(PaymentCreateCommandDTO command) {
        acquireSlot();
        Connection connection = null;
        try {
            connection = protectedConnection();
            JdbcTemplate protectedJdbc = statements(connection);
            requireAuthorized(protectedJdbc, readAdmissionFacts(protectedJdbc, command));
        } catch (SQLException | DataAccessException exception) {
            throw protectionUnavailable(exception);
        } finally {
            release(connection);
            protectedPreparations.release();
        }
    }

    /** 受保护的最终检查通过后，即使提交跨过到期时点也允许把本次准备提交。 */
    public <T> T protect(PaymentCreateCommandDTO command, PaymentRouteResultDTO plannedRoute,
                         String action, Supplier<T> prepare) {
        if (!enabled) {
            return prepare.get();
        }
        acquireSlot();
        Connection connection = null;
        try {
            FrozenMerchantFeeVersionSnapshotDTO frozenFee = freezeFee(command);
            connection = protectedConnection();
            JdbcTemplate locked = statements(connection);
            verify(locked, command, plannedRoute, action,
                    frozenFee == null ? null : frozenFee.snapshot().feePlanVersionId());
            command.setProtectedFeeVersion(frozenFee);
            return prepare.get();
        } catch (SQLException exception) {
            throw protectionUnavailable(exception);
        } finally {
            command.setProtectedFeeVersion(null);
            command.setPaymentAdmissionSnapshotJson(null);
            release(connection);
            protectedPreparations.release();
        }
    }

    private void acquireSlot() {
        try {
            if (protectedPreparations.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        throw new ServiceException(ApiResultEnum.NETWORK_BUSY);
    }

    private Connection protectedConnection() throws SQLException {
        DynamicRoutingDataSource routing = dataSourceProvider.getIfAvailable();
        if (routing == null) {
            throw new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE);
        }
        Connection connection = routing.getDataSource(DataSourceName.MASTER).getConnection();
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private JdbcTemplate statements(Connection connection) {
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.setQueryTimeout(3);
        return jdbc;
    }

    private ServiceException protectionUnavailable(Exception cause) {
        return new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                "Unable to complete merchant payment admission protection", cause);
    }

    /**
     * 冻结当前准入判定选中的费率版本，供交易准备持久化。
     */
    private FrozenMerchantFeeVersionSnapshotDTO freezeFee(PaymentCreateCommandDTO command) {
        try {
            return fees.freezeActiveVersion(command.getMerchantId(), LocalDateTime.now());
        } catch (ServiceException failure) {
            if (!ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode().equals(failure.getCode())) {
                throw failure;
            }
            // 由持锁检查归类业务缺口，数据库/运行故障仍沿用异常处理。
            return null;
        }
    }

    private void verify(JdbcTemplate locked, PaymentCreateCommandDTO command, PaymentRouteResultDTO plannedRoute,
                        String action, Long feeVersion) {
        var facts = readAdmissionFacts(locked, command);
        String merchant = command.getMerchantId();
        String itemKey = facts.itemKey();
        Mode mode = facts.mode();
        protectRouteRows(locked, merchant, itemKey, mode.revision());
        protectFee(locked, merchant, feeVersion, action, itemKey, mode.revision());
        LocalDateTime checkedAt = requireAuthorized(locked, facts);
        if (plannedRoute != null) {
            var candidate = router.assessCandidates(routeProfileReader.findFresh(merchant), command, checkedAt);
            String plannedCurrency = plannedRoute.getRoutedCurrency() == null
                    ? command.getLabelCurrency() : plannedRoute.getRoutedCurrency();
            if (!Objects.equals(candidate.channelId(), plannedRoute.getChannelId())
                    || !Objects.equals(candidate.midId(), plannedRoute.getMidConfigId())
                    || !Objects.equals(candidate.routedCurrency(), plannedCurrency)) {
                throw unavailable(itemKey, Reason.ROUTE_UNAVAILABLE, mode.revision());
            }
        }
        var grant = facts.grant();
        command.setPaymentAdmissionSnapshotJson(JsonUtils.toJsonString(new Proof(1, itemKey, mode.code(),
                grant == null ? null : grant.version(), checkedAt, feeVersion,
                plannedRoute == null ? null : plannedRoute.getMidConfigId())));
    }

    private AdmissionFacts readAdmissionFacts(JdbcTemplate locked, PaymentCreateCommandDTO command) {
        String merchant = command.getMerchantId();
        String brand = command.getTransactionInfo() == null ? null : command.getTransactionInfo().getCardBrand();
        String itemKey = MerchantAdmissionPolicy.itemKey(command.getPaymentMethod(), brand);
        if (itemKey == null) {
            throw unavailable(null, Reason.METHOD_UNRESOLVED, null);
        }
        var modes = locked.query("""
                SELECT payment_admission_mode, payment_baseline_batch_ref, payment_authorization_revision FROM base_merchant_info
                WHERE merchant_id = ? AND deleted = 0 AND merchant_status = 1 AND activation_status = 'ACTIVE'
                FOR SHARE
                """, (rs, index) -> new Mode(rs.getString(1), rs.getString(2), rs.getLong(3)), merchant);
        if (modes.isEmpty()) {
            throw unavailable(itemKey, Reason.MERCHANT_UNAVAILABLE, null);
        }
        Mode mode = modes.get(0);
        var grants = locked.query("""
                SELECT authorization_status, source, version, effective_time, expire_time
                FROM merchant_payment_authorization WHERE merchant_id = ? AND item_key = ? FOR SHARE
                """, (rs, index) -> new MerchantAdmissionPolicy.Grant(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getObject(4, LocalDateTime.class), rs.getObject(5, LocalDateTime.class)), merchant, itemKey);
        boolean legacy = "LEGACY_OBSERVE".equals(mode.code()) && mode.batch() != null
                && !locked.queryForList("""
                        SELECT id FROM merchant_review_record WHERE merchant_id = ? AND review_scope = 'LEGACY_SCOPE'
                          AND request_no = ? AND target_key = ? AND review_action = 'BASELINE_APPROVE'
                          AND to_status = 'APPROVED' ORDER BY id FOR SHARE
                        """, Long.class, merchant, mode.batch(), itemKey).isEmpty();
        if (!directoryEnabled(locked, itemKey)) {
            throw unavailable(itemKey, Reason.METHOD_DISABLED, mode.revision());
        }
        return new AdmissionFacts(itemKey, mode, grants.isEmpty() ? null : grants.get(0), legacy);
    }

    private LocalDateTime requireAuthorized(JdbcTemplate locked, AdmissionFacts facts) {
        LocalDateTime checkedAt = locked.queryForObject("SELECT CURRENT_TIMESTAMP(3)", LocalDateTime.class);
        if (checkedAt == null || !MerchantAdmissionPolicy.permits(facts.mode().code(),
                facts.grant(), facts.legacy(), checkedAt)) {
            throw unavailable(facts.itemKey(), Reason.AUTHORIZATION_UNAVAILABLE, facts.mode().revision());
        }
        return checkedAt;
    }

    private boolean directoryEnabled(JdbcTemplate locked, String itemKey) {
        locked.queryForList("""
                SELECT id FROM sys_dict_type WHERE dict_type IN ('acquiring_payment_method', 'card_brand')
                  AND deleted = 0 ORDER BY dict_type, id FOR SHARE
                """, Long.class);
        if (itemKey.startsWith("CARD_BRAND:")) {
            return enabledValue(locked, "acquiring_payment_method", "BANK_CARD")
                    && enabledValue(locked, "card_brand", itemKey.substring("CARD_BRAND:".length()));
        }
        return enabledValue(locked, "acquiring_payment_method", itemKey.substring("METHOD:".length()));
    }

    private boolean enabledValue(JdbcTemplate locked, String type, String value) {
        List<Integer> statuses = locked.queryForList("""
                SELECT data.status FROM sys_dict_data data
                JOIN sys_dict_type type ON type.dict_type = data.dict_type AND type.deleted = 0 AND type.status = 1
                WHERE data.dict_type = ? AND data.dict_value = ? AND data.deleted = 0
                ORDER BY data.id FOR SHARE OF data
                """, Integer.class, type, value);
        return !statuses.isEmpty() && statuses.stream().allMatch(status -> Integer.valueOf(1).equals(status));
    }

    private void protectRouteRows(JdbcTemplate locked, String merchant, String itemKey, long revision) {
        List<Long> mids = locked.queryForList("""
                SELECT mid_config_id FROM merchant_channel_mid_binding
                WHERE merchant_id = ? AND deleted = 0
                ORDER BY id
                """, Long.class, merchant);
        var named = new NamedParameterJdbcTemplate(locked);
        if (!mids.isEmpty()) {
            List<Long> channels = named.queryForList("""
                    SELECT DISTINCT channel_id FROM channel_mid_config WHERE id IN (:ids) ORDER BY channel_id
                    """, Map.of("ids", mids), Long.class);
            if (!channels.isEmpty()) {
                named.queryForList("SELECT id FROM channel_info WHERE id IN (:ids) ORDER BY id FOR SHARE",
                        Map.of("ids", channels), Long.class);
                List<Long> capabilities = named.queryForList("""
                        SELECT id FROM channel_payment_capability WHERE channel_id IN (:ids)
                          AND business_type = 'ACQUIRING' ORDER BY id FOR SHARE
                        """, Map.of("ids", channels), Long.class);
                if (!capabilities.isEmpty()) {
                    named.queryForList("""
                            SELECT id FROM channel_capability_currency WHERE capability_id IN (:ids) ORDER BY id FOR SHARE
                            """, Map.of("ids", capabilities), Long.class);
                    named.queryForList("""
                            SELECT id FROM channel_capability_card_brand WHERE capability_id IN (:ids) ORDER BY id FOR SHARE
                            """, Map.of("ids", capabilities), Long.class);
                }
            }
            named.queryForList("SELECT id FROM channel_mid_config WHERE id IN (:ids) ORDER BY id FOR SHARE",
                    Map.of("ids", mids), Long.class);
        }
        List<Long> protectedMids = locked.queryForList("""
                SELECT mid_config_id FROM merchant_channel_mid_binding
                WHERE merchant_id = ? AND deleted = 0
                ORDER BY id FOR SHARE
                """, Long.class, merchant);
        // 配置变化穿过首轮定位时拒绝复用原路由计划，避免保护到错误的父实体。
        if (!mids.equals(protectedMids)) {
            throw unavailable(itemKey, Reason.CONFIGURATION_CHANGED, revision);
        }
    }

    /**
     * 保护已选费率事实，避免支付提交窗口内读到相互矛盾的版本。
     */
    private void protectFee(JdbcTemplate locked, String merchant, Long expectedFeeVersion, String action,
                            String itemKey, long revision) {
        if (expectedFeeVersion == null) {
            throw unavailable(itemKey, Reason.FEE_UNAVAILABLE, revision);
        }
        List<Long> versions = locked.queryForList("""
                SELECT current_version_id FROM fee_plan WHERE merchant_id = ? AND plan_type = 'MERCHANT'
                  AND status = 'ENABLED' AND deleted = 0 ORDER BY id FOR SHARE
                """, Long.class, merchant);
        if (versions.size() != 1 || !expectedFeeVersion.equals(versions.get(0))) {
            throw unavailable(itemKey, Reason.CONFIGURATION_CHANGED, revision);
        }
        List<Long> active = locked.queryForList("""
                SELECT id FROM fee_plan_version WHERE id = ? AND version_status = 'ACTIVE' AND deleted = 0 FOR SHARE
                """, Long.class, expectedFeeVersion);
        if (active.isEmpty()) {
            throw unavailable(itemKey, Reason.FEE_UNAVAILABLE, revision);
        }
        boolean card = itemKey.startsWith("CARD_BRAND:");
        String code = itemKey.substring(itemKey.indexOf(':') + 1);
        List<Long> required = locked.queryForList("""
                SELECT id FROM fee_rule WHERE plan_version_id = ? AND deleted = 0
                  AND fee_category = 'TRANSACTION_FEE' AND risk_service_type = 'NONE'
                  AND transaction_type IN ('ALL', ?)
                  AND payment_type IN ('ALL', ?) AND payment_method IN ('ALL', ?)
                ORDER BY id FOR SHARE
                """, Long.class, expectedFeeVersion, action, card ? "BANK_CARD" : code, card ? code : "ALL");
        if (required.isEmpty()) {
            throw unavailable(itemKey, Reason.FEE_UNAVAILABLE, revision);
        }
    }

    /**
     * 结束准入保护读事务并归还连接，分别记录回滚和归还异常。
     */
    private void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            // 外层连接只持有读锁，释放失败不得把已经提交的内层结果改成失败。
            connection.rollback();
        } catch (SQLException exception) {
            log.warn("释放准入保护读事务失败, event: PAYMENT_ADMISSION_READ_ROLLBACK_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
        } finally {
            try {
                connection.close();
            } catch (SQLException exception) {
                log.warn("关闭准入保护连接失败, event: PAYMENT_ADMISSION_READ_CONNECTION_CLOSE_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    private static PaymentAdmissionRejectedException unavailable(String itemKey, Reason reason, Long revision) {
        return new PaymentAdmissionRejectedException(itemKey, reason, revision);
    }

    private record Mode(String code, String batch, long revision) {
    }

    private record AdmissionFacts(String itemKey, Mode mode, MerchantAdmissionPolicy.Grant grant, boolean legacy) {
    }

    private record Proof(int schemaVersion, String itemKey, String admissionMode, Long authorizationVersion,
                         LocalDateTime checkedAt, Long feeVersionId, Long midConfigId) {
    }
}
