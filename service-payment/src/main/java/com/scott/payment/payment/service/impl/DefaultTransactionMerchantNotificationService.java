package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationLogDO;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.service.TransactionMerchantNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionMerchantNotificationService
 * @date : 2026-07-14 21:37
 * @email : scott_x@163.com
 * @description : 商户交易结果通知默认实现，位于 service-payment 服务实现层，按 transaction_date_time 分表扫描到期任务并记录每一次通知尝试。
 * @status : create
 */
@Slf4j
@Service
public class DefaultTransactionMerchantNotificationService implements TransactionMerchantNotificationService {

    /**
     * TRANSACTION MERCHANT NOTIFICATION TABLE，用于保存 Default Transaction Merchant Notification Service 中与 交易商户通知table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";

    /**
     * TRANSACTION MERCHANT NOTIFICATION LOG TABLE，用于保存 Default Transaction Merchant Notification Service 中与 交易商户通知日志table 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";

    /**
     * NOTIFY LOG PREFIX，用于保存 Default Transaction Merchant Notification Service 中与 通知日志prefix 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String NOTIFY_LOG_PREFIX = "TNL";

    /**
     * DEFAULT TIME ZONE，用于保存 Default Transaction Merchant Notification Service 中与 defaulttimezone 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * STATUS FAILED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * STATUS CLOSED，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String STATUS_CLOSED = "CLOSED";

    /**
     * BASE RETRY DELAY SECONDS，用于保存 Default Transaction Merchant Notification Service 中与 baseretrydelayseconds 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long BASE_RETRY_DELAY_SECONDS = 60L;

    /**
     * MAX FAIL REASON LENGTH，用于保存 Default Transaction Merchant Notification Service 中与 maxfailreasonlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int MAX_FAIL_REASON_LENGTH = 512;

    /**
     * notification Mapper 依赖，用于 Default Transaction Merchant Notification Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionMerchantNotificationMapper notificationMapper;

    /**
     * notification Log Mapper 依赖，用于 Default Transaction Merchant Notification Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionMerchantNotificationLogMapper notificationLogMapper;

    /**
     * sharding Data Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ShardingDataTemplate shardingDataTemplate;

    /**
     * rest Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final RestTemplate restTemplate;

    /**
     * 创建商户通知服务。
     *
     * @param notificationMapper 商户通知任务 Mapper
     * @param notificationLogMapper 商户通知日志 Mapper
     * @param shardingDataTemplate 分表数据访问统一入口
     * @param restTemplate 商户通知 HTTP 客户端
     */
    public DefaultTransactionMerchantNotificationService(TransactionMerchantNotificationMapper notificationMapper,
                                                         TransactionMerchantNotificationLogMapper notificationLogMapper,
                                                         ShardingDataTemplate shardingDataTemplate,
                                                         @Qualifier("merchantNotificationRestTemplate") RestTemplate restTemplate) {
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.shardingDataTemplate = shardingDataTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * 执行指定交易时间所在分表的到期商户通知。
     *
     * @param transactionDateTime 交易业务时间
     * @param limit 最大处理数量
     * @return 成功通知数量
     */
    @Override
    public int notifyDue(LocalDateTime transactionDateTime, int limit) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        long startNanos = System.nanoTime();
        String table = physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, transactionDateTime);
        List<TransactionMerchantNotificationDO> dueTasks = notificationMapper.selectDueForNotify(table, LocalDateTime.now(), limit);
        log.info("event: PAYMENT_MERCHANT_NOTIFY_DUE_START stage=NOTIFY_SCAN traceId: {} logicalTable: {} physicalTable: {} transactionDateTime: {} limit: {} taskCount: {}",
                TraceContext.getTraceId(),
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                table,
                transactionDateTime,
                limit,
                dueTasks.size());
        int successCount = 0;
        for (TransactionMerchantNotificationDO task : dueTasks) {
            if (notifySingle(table, task)) {
                successCount++;
            }
        }
        log.info("event: PAYMENT_MERCHANT_NOTIFY_DUE_END stage=NOTIFY_SCAN traceId: {} logicalTable: {} physicalTable: {} transactionDateTime: {} limit: {} taskCount: {} successCount: {} durationMs: {}",
                TraceContext.getTraceId(),
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                table,
                transactionDateTime,
                limit,
                dueTasks.size(),
                successCount,
                elapsedMillis(startNanos));
        return successCount;
    }

    /**
     * 按平台交易 ID 精准触发商户通知。
     *
     * @param transactionDateTime 交易业务时间
     * @param transactionId 平台当前交易 ID
     * @return true 表示商户通知成功
     */
    @Override
    public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (!StringUtils.hasText(transactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        long startNanos = System.nanoTime();
        String table = physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, transactionDateTime);
        TransactionMerchantNotificationDO task = notificationMapper.selectReadyByTransactionId(
                table, transactionId, LocalDateTime.now());
        boolean notified = task != null && notifySingle(table, task);
        log.info("event: PAYMENT_MERCHANT_NOTIFY_TRANSACTION_END stage=NOTIFY_TRIGGER traceId: {} logicalTable: {} physicalTable: {} transactionId: {} taskFound: {} notified: {} durationMs: {}",
                TraceContext.getTraceId(),
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                table,
                transactionId,
                task != null,
                notified,
                elapsedMillis(startNanos));
        return notified;
    }

    private boolean notifySingle(String notificationTable, TransactionMerchantNotificationDO task) {
        LocalDateTime beginTime = LocalDateTime.now();
        if (notificationMapper.markProcessing(notificationTable, task.getId(), task.getVersion(), beginTime) != 1) {
            log.info("event: PAYMENT_MERCHANT_NOTIFY_SKIP stage=NOTIFY_ATTEMPT traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} reason=processingLockMiss logicalTable: {} physicalTable: {}",
                    TraceContext.getTraceId(),
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId(),
                    task.getMerchantId(),
                    task.getMerchantOrderNo(),
                    TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                    notificationTable);
            return false;
        }
        int processingVersion = task.getVersion() == null ? 1 : task.getVersion() + 1;
        int attemptNo = task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1;
        log.info("event: PAYMENT_MERCHANT_NOTIFY_ATTEMPT_START stage=MERCHANT_NOTIFY traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} targetUrlHash: {} attemptCount: {} maxRetryCount: {} payloadLength: {} payloadSummary: {} signType: {} logicalTable: {} physicalTable: {}",
                TraceContext.getTraceId(),
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                task.getMerchantOrderNo(),
                safeCallbackUrl(task.getTargetUrlMasked(), resolveTargetUrl(task)),
                task.getTargetUrlHash(),
                attemptNo,
                safeMaxRetry(task),
                task.getPayloadJsonMasked() == null ? 0 : task.getPayloadJsonMasked().length(),
                safeLength(SensitiveDataMaskUtils.maskJsonSafely(task.getPayloadJsonMasked()), 1200),
                task.getSignType(),
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                notificationTable);
        NotifyAttemptResult result = executeHttpNotify(task, beginTime);
        LocalDateTime finishedTime = LocalDateTime.now();
        int notifyLogRows = insertNotifyLog(task, attemptNo, result, beginTime, finishedTime);
        if (result.success()) {
            int affectedRows = notificationMapper.markSuccess(notificationTable, task.getId(), processingVersion, finishedTime);
            log.info("event: PAYMENT_MERCHANT_NOTIFY_ATTEMPT_END stage=MERCHANT_NOTIFY traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} attemptCount: {} httpStatus: {} merchantResponseSummary: {} success=true nextRetryTime: {} logicalTable: {} physicalTable: {} affectedRows: {} notifyLogRows: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId(),
                    task.getMerchantId(),
                    task.getMerchantOrderNo(),
                    safeCallbackUrl(result.callbackUrlMasked(), task.getTargetUrlMasked()),
                    attemptNo,
                    result.httpStatus(),
                    merchantResponseSummary(result.responseBody()),
                    null,
                    TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                    notificationTable,
                    affectedRows,
                    notifyLogRows,
                    durationMillis(beginTime, finishedTime));
            return true;
        }
        boolean exhausted = attemptNo >= safeMaxRetry(task);
        LocalDateTime nextRetryTime = exhausted ? null : nextRetryTime(finishedTime, attemptNo);
        int affectedRows = notificationMapper.markFailed(
                notificationTable,
                task.getId(),
                processingVersion,
                exhausted ? STATUS_CLOSED : STATUS_FAILED,
                nextRetryTime,
                safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                finishedTime);
        log.warn("event: PAYMENT_MERCHANT_NOTIFY_ATTEMPT_END stage=MERCHANT_NOTIFY traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} attemptCount: {} httpStatus: {} merchantResponseSummary: {} failureReason: {} success=false nextStatus: {} exhausted: {} nextRetryTime: {} logicalTable: {} physicalTable: {} affectedRows: {} notifyLogRows: {} durationMs: {}",
                TraceContext.getTraceId(),
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                task.getMerchantOrderNo(),
                safeCallbackUrl(result.callbackUrlMasked(), task.getTargetUrlMasked()),
                attemptNo,
                result.httpStatus(),
                merchantResponseSummary(result.responseBody()),
                safeLength(result.errorMessage(), 200),
                exhausted ? STATUS_CLOSED : STATUS_FAILED,
                exhausted,
                nextRetryTime,
                TRANSACTION_MERCHANT_NOTIFICATION_TABLE,
                notificationTable,
                affectedRows,
                notifyLogRows,
                durationMillis(beginTime, finishedTime));
        return false;
    }

    private NotifyAttemptResult executeHttpNotify(TransactionMerchantNotificationDO task, LocalDateTime beginTime) {
        String targetUrl = resolveTargetUrl(task);
        if (!StringUtils.hasText(targetUrl)) {
            return new NotifyAttemptResult(false, null, null, "merchant callback url is empty",
                    safeCallbackUrl(task.getTargetUrlMasked(), null));
        }
        String callbackUrlMasked = safeCallbackUrl(task.getTargetUrlMasked(), targetUrl);
        String requestBody = task.getPayloadJsonMasked();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-OPGS-Notify-Id", task.getNotifyId());
            headers.set("X-OPGS-Transaction-Id", task.getTransactionId());
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, new HttpEntity<>(requestBody, headers), String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            String responseBody = response.getBody();
            return new NotifyAttemptResult(
                    success,
                    response.getStatusCode().value(),
                    SensitiveDataMaskUtils.maskJsonSafely(responseBody),
                    success ? null : "merchant callback http status " + response.getStatusCode().value(),
                    callbackUrlMasked);
        } catch (RestClientException exception) {
            log.warn("event: PAYMENT_MERCHANT_NOTIFY_HTTP_FAILED stage=MERCHANT_NOTIFY_HTTP traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} exceptionType: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId(),
                    task.getMerchantId(),
                    task.getMerchantOrderNo(),
                    callbackUrlMasked,
                    exception.getClass().getSimpleName(),
                    durationMillis(beginTime, LocalDateTime.now()));
            return new NotifyAttemptResult(false, null, null, exception.getMessage(), callbackUrlMasked);
        }
    }

    /**
     * 写入一次商户通知尝试的审计日志。
     * <p>
     * 前置条件：调用方已经完成通知 HTTP 调用，响应体、请求体和错误信息必须只保留脱敏文本或长度受控摘要。
     * 该方法按交易业务时间定位通知日志物理分表，写入 notifyId、transactionId、operationId、尝试次数、
 * HTTP 状态、成功标记和耗时，供商户对接问题追溯。
 * 异常边界：数据库写入失败由当前事务向外抛出；不记录完整卡号、密钥、认证头或完整敏感响应。
 * </p>
 * @param task 商户通知任务，提供分表时间、通知编号、平台交易号、商户号和回调地址摘要
 * @param attemptNo 本次通知尝试序号，从 1 开始递增
 * @param result 商户 HTTP 响应或异常摘要，响应体必须已脱敏
 * @param beginTime 本次通知开始时间
 * @param finishedTime 本次通知结束时间
     * @return 通知日志表影响行数
     */
    private int insertNotifyLog(TransactionMerchantNotificationDO task,
                                 int attemptNo,
                                 NotifyAttemptResult result,
                                 LocalDateTime beginTime,
                                 LocalDateTime finishedTime) {
        TransactionMerchantNotificationLogDO logDO = new TransactionMerchantNotificationLogDO();
        logDO.setNotifyLogId(PaymentOrderNoGenerator.nextOrderNo(NOTIFY_LOG_PREFIX, task.getTransactionDateTime()));
        logDO.setNotifyId(task.getNotifyId());
        logDO.setTransactionId(task.getTransactionId());
        logDO.setOperationId(task.getOperationId());
        logDO.setMerchantId(task.getMerchantId());
        logDO.setAttemptNo(attemptNo);
        logDO.setTargetUrlHash(task.getTargetUrlHash());
        logDO.setHttpStatus(result.httpStatus());
        logDO.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                "Content-Type", MediaType.APPLICATION_JSON_VALUE,
                "X-OPGS-Notify-Id", task.getNotifyId(),
                "X-OPGS-Transaction-Id", task.getTransactionId())));
        logDO.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(task.getPayloadJsonMasked()));
        logDO.setResponseBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(result.responseBody()));
        logDO.setSuccess(result.success() ? 1 : 0);
        logDO.setErrorMessage(safeLength(result.errorMessage(), 1024));
        logDO.setNotifyTime(beginTime);
        logDO.setDurationMillis(durationMillis(beginTime, finishedTime));
        fillTransactionTime(logDO, task.getTransactionDateTime());
        logDO.setCreateTime(finishedTime);
        String physicalTable = physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, task.getTransactionDateTime());
        int affectedRows = notificationLogMapper.insertPhysical(
                physicalTable,
                logDO);
        log.info("event: PAYMENT_MERCHANT_NOTIFY_LOG_SAVED stage=MERCHANT_NOTIFY_LOG traceId: {} notifyId: {} notifyLogId: {} transactionId: {} operationId: {} merchantId: {} attemptCount: {} httpStatus: {} success: {} logicalTable: {} physicalTable: {} affectedRows: {} durationMs: {}",
                TraceContext.getTraceId(),
                task.getNotifyId(),
                logDO.getNotifyLogId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                attemptNo,
                result.httpStatus(),
                result.success(),
                TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE,
                physicalTable,
                affectedRows,
                logDO.getDurationMillis());
        return affectedRows;
    }

    /**
     * 解析resolvetargeturl，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 支付核心服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param task task 输入值，参与 task 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveTargetUrl(TransactionMerchantNotificationDO task) {
        if (!StringUtils.hasText(task.getNotifyConfigSnapshotJson())) {
            return null;
        }
        JSONObject snapshot = JsonUtils.parseObject(task.getNotifyConfigSnapshotJson(), JSONObject.class);
        return snapshot == null ? null : snapshot.getString("callbackUrl");
    }

    /**
     * 整理nextretry时间，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param baseTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param attemptNo attempt No 输入值，参与 attemptno 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime nextRetryTime(LocalDateTime baseTime, int attemptNo) {
        long delaySeconds = BASE_RETRY_DELAY_SECONDS * Math.max(1, Math.min(attemptNo, 30));
        return baseTime.plusSeconds(delaySeconds);
    }

    /**
     * 规范化maxretry，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param task task 输入值，参与 task 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int safeMaxRetry(TransactionMerchantNotificationDO task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() <= 0 ? 1 : task.getMaxRetryCount();
    }

    /**
     * 整理duration毫秒数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    /**
     * 生成商户通知响应摘要。
     * <p>
     * 响应体已经经过 JSON 脱敏，这里再做长度限制，避免商户返回大 HTML 或长错误栈撑大业务日志。
     * </p>
     * @param responseBodyMasked 商户响应脱敏文本
     * @return 响应摘要
     */
    private String merchantResponseSummary(String responseBodyMasked) {
        return safeLength(SensitiveDataMaskUtils.maskJsonSafely(responseBodyMasked), 1200);
    }

    /**
     * 返回第一个有文本内容的字符串。
     *
     * @param values 候选字符串
     * @return 第一个非空白字符串
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从候选 URL 中取第一个有效值并统一删除 query 值。
     * <p>
     * 前置条件：候选值可能来自历史脱敏字段或本次解析出的商户通知 URL，不可信任其已经完成脱敏。
     * 返回结果只用于日志和审计摘要，不影响真正 HTTP 调用的原始地址。
     * </p>
     * @param values 候选 URL 或 URL 摘要
     * @return 去除 query 值后的 URL 摘要
     */
    private String safeCallbackUrl(String... values) {
        return maskUrl(firstText(values));
    }

    /**
     * 脱敏 URL 查询参数。
     * <p>
     * 商户通知 URL 可能携带商户侧 token 或订单信息，日志只保留 scheme、host、path 和 query 存在性。
     * </p>
     * @param url 原始 URL
     * @return 去除 query 值后的 URL
     */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) {
            return url;
        }
        return url.substring(0, queryIndex) + "?...";
    }

    /**
     * 构造交易时间对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    private void fillTransactionTime(TransactionMerchantNotificationLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 整理物理表，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param logicalTable 逻辑表名，用于按交易时间解析真实物理分表
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
    }

    /**
     * 规范化length，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param maxLength max Length 输入值，参与 maxlength 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 整理耗时毫秒数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startNanos start Nanos 输入值，参与 startnanos 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record NotifyAttemptResult(boolean success,
                                       Integer httpStatus,
                                       String responseBody,
                                       String errorMessage,
                                       String callbackUrlMasked) {
    }
}
