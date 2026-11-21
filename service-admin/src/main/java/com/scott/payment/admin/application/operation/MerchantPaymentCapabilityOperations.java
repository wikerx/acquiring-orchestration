package com.scott.payment.admin.application.operation;

import com.scott.payment.capability.governance.MerchantPaymentCapabilityEventPublisher;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.capability.governance.MerchantPaymentCapabilityEventPublisher.Message;
import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentCapabilityEvaluationDTOs;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.impl.AdminEmailDeliveryService;
import com.scott.payment.admin.service.impl.JdbcAdminOperationalWorkItemService;
import com.scott.payment.admin.service.impl.JdbcAdminOperationalWorkItemService.CapabilityBasis;
import com.scott.payment.admin.service.impl.JdbcAdminOperationalWorkItemService.CapabilityTransition;
import com.scott.payment.admin.service.impl.JdbcAdminOperationalWorkItemService.ConfigurationBlockNotice;
import com.scott.payment.capability.governance.MerchantPaymentCapabilityReadinessService;
import com.scott.payment.capability.governance.MerchantPaymentCapabilityReadinessService.CapabilityKey;
import com.scott.payment.capability.governance.MerchantPaymentCapabilityReadinessService.ReadinessResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.mq.publisher.ReliableMqOutboxRelayService;
import com.scott.payment.capability.MerchantAdmissionReader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 支付方式待办、通知与 Job 自然边界处理的归属服务。 */
@Service
public class MerchantPaymentCapabilityOperations {

    private static final String PENDING = "PENDING_REVIEW";
    private static final String APPROVAL = JdbcAdminOperationalWorkItemService.PAYMENT_METHOD_APPROVAL;
    private static final String CONFIGURATION = JdbcAdminOperationalWorkItemService.PAYMENT_METHOD_CONFIGURATION;
    private static final long SOURCE_SLICE_NANOS = Duration.ofSeconds(2).toNanos();
    private static final Set<String> NON_NOTIFICATION = Set.of("CONFIGURATION_CHANGED", "WITHDRAW");
    private static final Set<String> CONFIGURATION_ACTIONS = Set.of("APPROVE", "SUSPEND", "RESTORE", "TERMINATE",
            "VALIDITY", "AUTH_EFFECTIVE", "AUTH_EXPIRED", "FEE_CHANGED", "CONFIGURATION_CHANGED");
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PaymentInternalClient payment;
    private final JdbcAdminOperationalWorkItemService workItems;
    private final MerchantPaymentCapabilityEventPublisher events;
    private final AdminEmailService email;
    private final AdminEmailDeliveryService delivery;
    private final ReliableMqOutboxRelayService relay;
    private final MerchantPaymentCapabilityReadinessService configuredCapabilities;
    private final MerchantAdmissionReader admission;

    public MerchantPaymentCapabilityOperations(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager,
            PaymentInternalClient payment, JdbcAdminOperationalWorkItemService workItems,
            MerchantPaymentCapabilityEventPublisher events, AdminEmailService email,
            AdminEmailDeliveryService delivery, ReliableMqOutboxRelayService relay,
            MerchantPaymentCapabilityReadinessService configuredCapabilities, MerchantAdmissionReader admission) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.payment = payment;
        this.workItems = workItems;
        this.events = events;
        this.email = email;
        this.delivery = delivery;
        this.relay = relay;
        this.configuredCapabilities = configuredCapabilities;
        this.admission = admission;
    }

    /** 消费已提交事件。重复消费重新读取权威事实，原业务通知按事件标识收敛。 */
    @DS(DataSourceName.MASTER)
    public void consume(Message message) {
        if (message.getApplicationItemId() != null) {
            synchronizeApproval(message);
        }
        try {
            if (CONFIGURATION_ACTIONS.contains(message.getAction())) {
                synchronizeConfiguration(message.getMerchantId(), message.getItemKey(), message.getOccurredAt());
            }
        } catch (RuntimeException capabilityFailure) {
            try {
                notifyIfApplicable(message);
            } catch (RuntimeException notifyFailure) {
                capabilityFailure.addSuppressed(notifyFailure);
            }
            throw capabilityFailure;
        }
        notifyIfApplicable(message);
    }

    private void notifyIfApplicable(Message message) {
        if (ConfigurationBlockNotice.ACTION.equals(message.getAction())) {
            notifyCurrentConfigurationBlock(message);
            return;
        }
        if (!NON_NOTIFICATION.contains(message.getAction()) && naturalNotificationStillApplies(message)) {
            sendNotification(message, message.getMessageId());
        }
    }

    private void notifyCurrentConfigurationBlock(Message message) {
        var notice = new ConfigurationBlockNotice(message.getConfigurationWorkItemNo(),
                message.getConfigurationGeneration());
        if (!workItems.isCurrentConfigurationBlock(message.getMerchantId(), notice.workItemNo(),
                notice.generation(), false)
                || !synchronizeConfiguration(message.getMerchantId(), message.getItemKey(), message.getOccurredAt())
                        .contains(message.getItemKey())) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            if (workItems.isCurrentConfigurationBlock(message.getMerchantId(), notice.workItemNo(),
                    notice.generation(), true)) {
                sendNotification(message, notice.emailBusinessNumber());
            }
        });
    }

    private boolean naturalNotificationStillApplies(Message message) {
        if (!"AUTH_EFFECTIVE".equals(message.getAction()) && !"AUTH_EXPIRED".equals(message.getAction())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        return grants(message.getMerchantId(), message.getItemKey()).stream().anyMatch(grant ->
                "AUTH_EFFECTIVE".equals(message.getAction())
                        ? grant.usableAt(now) && message.getOccurredAt().equals(grant.effective())
                        : "ACTIVE".equals(grant.status()) && message.getOccurredAt().equals(grant.expire())
                          && !now.isBefore(grant.expire()));
    }

    private void synchronizeApproval(Message message) {
        transaction.executeWithoutResult(status -> {
            List<Long> revisions = jdbc.query("""
                    SELECT payment_authorization_revision FROM base_merchant_info
                    WHERE merchant_id = :merchantId AND deleted = 0 FOR UPDATE
                    """, new MapSqlParameterSource("merchantId", message.getMerchantId()),
                    (row, index) -> row.getLong(1));
            if (revisions.isEmpty()) {
                return;
            }
            List<Application> applications = jdbc.query("""
                    SELECT application_no, round_no, status FROM merchant_payment_application_item
                    WHERE id = :id AND merchant_id = :merchantId AND item_key = :itemKey
                    """, new MapSqlParameterSource("id", message.getApplicationItemId())
                    .addValue("merchantId", message.getMerchantId()).addValue("itemKey", message.getItemKey()),
                    (row, index) -> new Application(row.getString(1), row.getInt(2), row.getString(3)));
            if (applications.isEmpty()) {
                return;
            }
            Application application = applications.get(0);
            boolean current = application.round() == message.getRoundNo();
            String state = current ? application.status() : "ROUND_SUPERSEDED";
            workItems.synchronizePaymentCapability(APPROVAL, message.getApplicationItemId() + ":" + message.getRoundNo(),
                    message.getMerchantId(), application.number(), message.getItemKey(), current && PENDING.equals(state),
                    new CapabilityBasis(state, List.of(), revisions.get(0), LocalDateTime.now()), message.getOccurredAt());
        });
    }

    private Set<String> synchronizeConfiguration(String merchantId, String itemKey, LocalDateTime occurredAt) {
        LocalDateTime measuredAt = LocalDateTime.now();
        List<Grant> grants = grants(merchantId, itemKey);
        if (grants.isEmpty()) {
            return Set.of();
        }
        Set<String> directory = admission.enabledDirectory();
        List<String> active = new ArrayList<>();
        for (Grant grant : grants) {
            if (grant.usableAt(measuredAt) && directory.contains(grant.itemKey())) {
                active.add(grant.itemKey());
            } else {
                String state = directory.contains(grant.itemKey()) ? "AUTHORIZATION_INACTIVE" : "PLATFORM_UNAVAILABLE";
                projectConfiguration(merchantId, grant, false,
                        new CapabilityBasis(state, List.of(), grant.revision(), measuredAt), occurredAt);
            }
        }
        if (active.isEmpty()) {
            return Set.of();
        }
        Map<String, PaymentCapabilityEvaluationDTOs.Method> methodByKey = evaluate(merchantId, active);
        ReadinessResult configured = null;
        if (methodByKey.values().stream().anyMatch(method -> "CONDITIONAL".equals(method.businessReadiness()))) {
            configured = configuredCapabilities.evaluateCurrent(merchantId, measuredAt,
                    () -> activeFeeVersion(merchantId)).acquiring();
        }
        Set<String> confirmedBlocked = new LinkedHashSet<>();
        for (Grant grant : grants) {
            if (!active.contains(grant.itemKey())) {
                continue;
            }
            PaymentCapabilityEvaluationDTOs.Method method = methodByKey.get(grant.itemKey());
            if (method == null || "UNKNOWN".equals(method.businessReadiness())) {
                throw new IllegalStateException("payment capability evaluation returned an unknown item");
            }
            if ("READY".equals(method.businessReadiness())
                    || ("CONDITIONAL".equals(method.businessReadiness()) && hasConfiguredPath(configured, grant.itemKey()))) {
                projectConfiguration(merchantId, grant, false,
                        new CapabilityBasis("READY", List.of(), grant.revision(), measuredAt), occurredAt);
            } else if (method.missingFee() || method.missingMid()
                    || hasRouteConflict(configured, grant.itemKey())
                    || (method.reasonCodes() != null && method.reasonCodes().contains("ROUTE_AMBIGUOUS"))) {
                List<String> reasons = new ArrayList<>();
                if (method.missingFee()) {
                    reasons.add("FEE_MISSING");
                }
                if (method.missingMid()) {
                    reasons.add("MID_MISSING");
                }
                if (hasRouteConflict(configured, grant.itemKey())
                        || (method.reasonCodes() != null && method.reasonCodes().contains("ROUTE_AMBIGUOUS"))) {
                    reasons.add("ROUTE_AMBIGUOUS");
                }
                projectConfiguration(merchantId, grant, true,
                        new CapabilityBasis("BLOCKED", reasons, grant.revision(), measuredAt), occurredAt);
                confirmedBlocked.add(grant.itemKey());
            }
        }
        return confirmedBlocked;
    }

    private Map<String, PaymentCapabilityEvaluationDTOs.Method> evaluate(String merchantId, List<String> itemKeys) {
        int limit = 64;
        Map<String, PaymentCapabilityEvaluationDTOs.Method> result = new LinkedHashMap<>();
        for (int from = 0; from < itemKeys.size(); from += limit) {
            List<String> batch = itemKeys.subList(from, Math.min(itemKeys.size(), from + limit));
            PaymentCapabilityEvaluationDTOs.Response assessment = payment.evaluateMerchantCapabilities(
                    new PaymentCapabilityEvaluationDTOs.Request(merchantId, batch, null, "PAYMENT"));
            if (assessment == null || !"EVALUATED".equals(assessment.assessmentStatus()) || assessment.methods() == null) {
                throw new IllegalStateException("payment capability evaluation was unavailable");
            }
            result.putAll(assessment.methods().stream().filter(method -> batch.contains(method.itemKey()))
                    .collect(Collectors.toMap(PaymentCapabilityEvaluationDTOs.Method::itemKey, method -> method)));
        }
        return result;
    }

    private Long activeFeeVersion(String merchantId) {
        return jdbc.query("""
                SELECT p.current_version_id FROM fee_plan p JOIN fee_plan_version v ON v.id = p.current_version_id
                WHERE p.merchant_id = :merchantId AND p.plan_type = 'MERCHANT' AND p.status = 'ENABLED'
                  AND p.deleted = 0 AND v.deleted = 0 AND v.version_status = 'ACTIVE'
                """, new MapSqlParameterSource("merchantId", merchantId), (row, index) -> row.getLong(1))
                .stream().findFirst().orElse(null);
    }

    private static boolean hasConfiguredPath(ReadinessResult configured, String itemKey) {
        return configured != null && configured.capabilities().stream().anyMatch(key -> matchesItem(key, itemKey)
                && !configured.feeGaps().contains(key) && !configured.feeAmbiguities().contains(key)
                && !configured.routeConflicts().contains(key));
    }

    private static boolean hasRouteConflict(ReadinessResult configured, String itemKey) {
        return configured != null && configured.routeConflicts().stream().anyMatch(key -> matchesItem(key, itemKey));
    }

    private static boolean matchesItem(CapabilityKey key, String itemKey) {
        return "PAYMENT".equals(key.transactionType()) && itemKey.equals("BANK_CARD".equals(key.paymentType())
                ? "CARD_BRAND:" + key.paymentMethod() : "METHOD:" + key.paymentType());
    }

    private void projectConfiguration(String merchantId, Grant grant, boolean active,
                                      CapabilityBasis basis, LocalDateTime occurredAt) {
        transaction.executeWithoutResult(status -> {
            CapabilityTransition transition = workItems.synchronizePaymentCapability(CONFIGURATION,
                    merchantId + ":" + grant.itemKey(), merchantId, merchantId, grant.itemKey(), active, basis, occurredAt);
            if (transition == null) {
                return;
            }
            if ("BLOCKED".equals(transition.status())) {
                var notice = new ConfigurationBlockNotice(transition.workItemNo(), transition.version());
                events.publishConfigurationBlocked(merchantId, grant.itemKey(), notice.workItemNo(), notice.generation(),
                        notice.emailBusinessNumber(), basis.measuredAt());
            } else {
                events.publishReady(merchantId, grant.itemKey(),
                        transition.workItemNo() + ":" + transition.version(), basis.measuredAt());
            }
        });
    }

    private List<Grant> grants(String merchantId, String itemKey) {
        String filter = itemKey == null ? "" : " AND a.item_key = :itemKey";
        return jdbc.query("""
                SELECT a.item_key, a.authorization_status, a.effective_time, a.expire_time,
                    m.payment_authorization_revision
                FROM merchant_payment_authorization a JOIN base_merchant_info m ON m.merchant_id = a.merchant_id
                WHERE a.merchant_id = :merchantId AND m.deleted = 0
                """ + filter + " ORDER BY a.id",
                new MapSqlParameterSource("merchantId", merchantId).addValue("itemKey", itemKey),
                (row, index) -> new Grant(row.getString(1), row.getString(2),
                        row.getObject(3, LocalDateTime.class), row.getObject(4, LocalDateTime.class), row.getLong(5)));
    }

    private void sendNotification(Message message, String businessNumber) {
        List<MerchantContact> contacts = jdbc.query("""
                SELECT contact_email, default_locale FROM base_merchant_info
                WHERE merchant_id = :merchantId AND deleted = 0
                """, new MapSqlParameterSource("merchantId", message.getMerchantId()),
                (row, index) -> new MerchantContact(row.getString(1), row.getString(2)));
        if (contacts.isEmpty() || contacts.get(0).email() == null || contacts.get(0).email().isBlank()) {
            throw new IllegalStateException("payment capability notification has no merchant recipient");
        }
        MerchantContact contact = contacts.get(0);
        EmailSendRequest request = new EmailSendRequest();
        request.setAppCode("ADMIN");
        request.setBizType(AdminEmailDeliveryService.PAYMENT_CAPABILITY_BIZ_TYPE);
        request.setBizNo(businessNumber);
        request.setMerchantId(message.getMerchantId());
        request.setMerchantNo(message.getMerchantId());
        request.setTemplateCode("MERCHANT_PAYMENT_CAPABILITY");
        request.setLocale(contact.locale());
        request.setHtmlEscapeVariables(true);
        request.setToEmails(List.of(contact.email()));
        request.setVariables(Map.of("merchantId", message.getMerchantId(), "action", message.getAction(),
                "itemKey", message.getItemKey() == null ? "" : message.getItemKey(),
                "occurredAt", message.getOccurredAt().toString()));
        byte[] key = MerchantPaymentCapabilityEventPublisher.digest(message.getMessageId(), "merchant-contact", "email");
        byte[] payloadHash = MerchantPaymentCapabilityEventPublisher.digest(message.getMessageId(),
                message.getMerchantId(), message.getItemKey(), message.getAction(),
                message.getApplicationItemId(), message.getRoundNo(), message.getOccurredAt());
        if (ConfigurationBlockNotice.ACTION.equals(message.getAction())) {
            payloadHash = MerchantPaymentCapabilityEventPublisher.digest(payloadHash, businessNumber);
        }
        email.sendPaymentCapabilityNotification(request, key, payloadHash);
    }

    /** Job 给出固定上界和已确认位置，扫描成功后才返回可推进游标。 */
    @DS(DataSourceName.MASTER)
    public BoundaryResponse boundaries(BoundaryRequest request) {
        LocalDateTime maximum = LocalDateTime.now().plusSeconds(2);
        if (request == null || request.source() == null || request.limit() < 1 || request.limit() > 100
                || request.after() == null || request.after().time() == null || request.after().sourceId() < 0
                || request.through() == null || request.through().isAfter(maximum)
                || request.after().time().isAfter(request.through())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "payment capability boundary range is invalid");
        }
        BoundarySource source;
        try {
            source = BoundarySource.valueOf(request.source());
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "payment capability boundary source is invalid");
        }
        String sql = "SELECT boundary_id, occurred_at, merchant_id, item_key FROM ("
                + source.selection + """
                ) source_rows WHERE occurred_at <= :through
                  AND (occurred_at > :afterTime OR (occurred_at = :afterTime AND boundary_id > :afterId))
                ORDER BY occurred_at, boundary_id LIMIT :take
                """;
        List<Boundary> rows = jdbc.query(sql, new MapSqlParameterSource("through", request.through())
                .addValue("afterTime", request.after().time()).addValue("afterId", request.after().sourceId())
                .addValue("take", request.limit() + 1), (row, index) -> new Boundary(
                        row.getLong(1), row.getObject(2, LocalDateTime.class), row.getString(3), row.getString(4)));
        int processed = 0;
        long started = System.nanoTime();
        for (Boundary boundary : rows) {
            if (processed >= request.limit() || (processed > 0 && System.nanoTime() - started >= SOURCE_SLICE_NANOS)) {
                break;
            }
            if (source == BoundarySource.AUTH_EFFECTIVE || source == BoundarySource.AUTH_EXPIRY) {
                transaction.executeWithoutResult(status -> events.publishAuthorizationBoundary(
                        boundary.merchantId(), boundary.itemKey(), source == BoundarySource.AUTH_EXPIRY,
                        source.name() + ":" + boundary.id() + ":" + boundary.occurredAt(), boundary.occurredAt()));
            } else {
                synchronizeConfiguration(boundary.merchantId(), boundary.itemKey(), boundary.occurredAt());
            }
            processed++;
        }
        Boundary last = processed == 0 ? null : rows.get(processed - 1);
        Cursor next = last == null ? null : new Cursor(last.occurredAt(), last.id());
        return new BoundaryResponse(next, rows.size() > processed, processed);
    }

    /** 旧邮件扫描排除此业务类型，此处仅由 Job 恢复本业务及归属发件箱。 */
    @DS(DataSourceName.MASTER)
    public RecoveryResponse recover(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        int requeued = delivery.recoverPaymentCapabilityDelivery(limit);
        int relayed = relay.recoverPaymentCapabilityDelivery(limit);
        return new RecoveryResponse(requeued, relayed);
    }

    private enum BoundarySource {
        AUTH_EFFECTIVE("""
                SELECT id boundary_id, effective_time occurred_at, merchant_id, item_key
                FROM merchant_payment_authorization WHERE authorization_status='ACTIVE' AND effective_time > gmt_modified
                """),
        /**
         * 支付方式授权在本次扫描窗口到期。
         */
        AUTH_EXPIRY("SELECT id boundary_id, expire_time occurred_at, merchant_id, item_key FROM merchant_payment_authorization WHERE authorization_status='ACTIVE'"),
        /**
         * 商户与渠道商户号绑定到达生效时间。
         */
        BINDING_EFFECTIVE("SELECT id boundary_id, effective_time occurred_at, merchant_id, NULL item_key FROM merchant_channel_mid_binding WHERE deleted=0"),
        /**
         * 商户与渠道商户号绑定到达失效时间。
         */
        BINDING_EXPIRY("SELECT id boundary_id, expire_time occurred_at, merchant_id, NULL item_key FROM merchant_channel_mid_binding WHERE deleted=0"),
        /**
         * 渠道商户号配置到达生效时间。
         */
        MID_EFFECTIVE(mid("effective_time")),
        /**
         * 渠道商户号配置到达失效时间。
         */
        MID_EXPIRY(mid("expire_time")),
        /**
         * 绑定资料变更后需要重新评估可用性。
         */
        BINDING_CHANGE("SELECT id boundary_id, update_time occurred_at, merchant_id, NULL item_key FROM merchant_channel_mid_binding"),
        /**
         * 渠道商户号配置变更后需要重新评估受影响的商户。
         */
        MID_CHANGE(mid("update_time")),
        CHANNEL_CHANGE("""
                SELECT b.id boundary_id, c.update_time occurred_at, b.merchant_id, NULL item_key
                FROM channel_info c JOIN merchant_channel_mid_binding b ON b.channel_id=c.id WHERE b.deleted=0
                """),
        CAPABILITY_CHANGE("""
                SELECT DISTINCT b.id boundary_id, c.update_time occurred_at, b.merchant_id, NULL item_key
                FROM channel_payment_capability c JOIN merchant_channel_mid_binding b ON b.channel_id=c.channel_id
                WHERE b.deleted=0 AND c.business_type='ACQUIRING'
                """),
        /**
         * 商户费率版本变动后需要刷新支付能力结论。
         */
        FEE_CHANGE("SELECT id boundary_id, update_time occurred_at, merchant_id, NULL item_key FROM fee_plan WHERE plan_type='MERCHANT'"),
        DICTIONARY_CHANGE("""
                SELECT DISTINCT a.id boundary_id, d.updated_at occurred_at, a.merchant_id, a.item_key
                FROM sys_dict_data d JOIN merchant_payment_authorization a
                  ON (d.dict_type = 'card_brand' AND a.item_key=CONCAT('CARD_BRAND:', d.dict_value))
                  OR (d.dict_type = 'acquiring_payment_method' AND
                     (a.item_key=CONCAT('METHOD:',d.dict_value)
                       OR (d.dict_value='BANK_CARD' AND a.item_key LIKE 'CARD_BRAND:%')))
                """),
        DICTIONARY_POLICY_CHANGE("""
                SELECT DISTINCT a.id boundary_id, d.updated_at occurred_at, a.merchant_id, a.item_key
                FROM sys_dict_type d JOIN merchant_payment_authorization a
                  ON (d.dict_type='card_brand' AND a.item_key LIKE 'CARD_BRAND:%')
                    OR d.dict_type='acquiring_payment_method'
                """);

        private final String selection;

        BoundarySource(String selection) {
            this.selection = selection;
        }

        private static String mid(String timeColumn) {
            return "SELECT b.id boundary_id, c." + timeColumn + """
                     occurred_at, b.merchant_id, NULL item_key
                    FROM channel_mid_config c JOIN merchant_channel_mid_binding b ON b.mid_config_id=c.id
                    WHERE b.deleted=0
                    """;
        }
    }

    public record Cursor(LocalDateTime time, long sourceId) {
    }

    public record BoundaryRequest(String source, Cursor after, LocalDateTime through, int limit) {
    }

    public record BoundaryResponse(Cursor next, boolean hasMore, int processed) {
    }

    public record RecoveryRequest(int limit) {
    }

    public record RecoveryResponse(int requeued, int relayed) {
    }

    private record Boundary(long id, LocalDateTime occurredAt, String merchantId, String itemKey) {
    }

    private record MerchantContact(String email, String locale) {
    }

    private record Application(String number, int round, String status) {
    }

    private record Grant(String itemKey, String status, LocalDateTime effective, LocalDateTime expire, long revision) {
        boolean usableAt(LocalDateTime now) {
            return "ACTIVE".equals(status) && effective != null && !now.isBefore(effective)
                    && (expire == null || now.isBefore(expire));
        }
    }
}
