package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
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
     * TRANSACTION MERCHANT NOTIFICATION TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";

    /**
     * TRANSACTION MERCHANT NOTIFICATION LOG TABLE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";

    /**
     * NOTIFY LOG PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String NOTIFY_LOG_PREFIX = "TNL";

    /**
     * DEFAULT TIME ZONE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * STATUS FAILED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * STATUS CLOSED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String STATUS_CLOSED = "CLOSED";

    /**
     * BASE RETRY DELAY SECONDS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long BASE_RETRY_DELAY_SECONDS = 60L;

    /**
     * MAX FAIL REASON LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_FAIL_REASON_LENGTH = 512;

    /**
     * notification Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantNotificationMapper notificationMapper;

    /**
     * notification Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantNotificationLogMapper notificationLogMapper;

    /**
     * sharding Data Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingDataTemplate shardingDataTemplate;

    /**
     * rest Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
        log.info("event=PAYMENT_MERCHANT_NOTIFY_DUE_START table={} transactionDateTime={} limit={} taskCount={}",
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
        log.info("event=PAYMENT_MERCHANT_NOTIFY_DUE_END table={} transactionDateTime={} limit={} taskCount={} successCount={} durationMs={}",
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
        log.info("event=PAYMENT_MERCHANT_NOTIFY_TRANSACTION_END table={} transactionId={} taskFound={} notified={} durationMs={}",
                table,
                transactionId,
                task != null,
                notified,
                elapsedMillis(startNanos));
        return notified;
    }

    /**
     * 发送 notify Single 对应的外部通知、内部消息或远程请求。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param notificationTable notification Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private boolean notifySingle(String notificationTable, TransactionMerchantNotificationDO task) {
        LocalDateTime beginTime = LocalDateTime.now();
        if (notificationMapper.markProcessing(notificationTable, task.getId(), task.getVersion(), beginTime) != 1) {
            log.info("event=PAYMENT_MERCHANT_NOTIFY_SKIP notifyId={} transactionId={} operationId={} reason=processingLockMiss",
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId());
            return false;
        }
        int processingVersion = task.getVersion() == null ? 1 : task.getVersion() + 1;
        int attemptNo = task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1;
        log.info("event=PAYMENT_MERCHANT_NOTIFY_ATTEMPT_START notifyId={} transactionId={} operationId={} merchantId={} merchantOrderNo={} attemptNo={} maxRetryCount={}",
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                task.getMerchantOrderNo(),
                attemptNo,
                safeMaxRetry(task));
        NotifyAttemptResult result = executeHttpNotify(task, beginTime);
        LocalDateTime finishedTime = LocalDateTime.now();
        insertNotifyLog(task, attemptNo, result, beginTime, finishedTime);
        if (result.success()) {
            notificationMapper.markSuccess(notificationTable, task.getId(), processingVersion, finishedTime);
            log.info("event=PAYMENT_MERCHANT_NOTIFY_ATTEMPT_END notifyId={} transactionId={} operationId={} attemptNo={} httpStatus={} success=true durationMs={}",
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId(),
                    attemptNo,
                    result.httpStatus(),
                    durationMillis(beginTime, finishedTime));
            return true;
        }
        boolean exhausted = attemptNo >= safeMaxRetry(task);
        notificationMapper.markFailed(
                notificationTable,
                task.getId(),
                processingVersion,
                exhausted ? STATUS_CLOSED : STATUS_FAILED,
                exhausted ? null : nextRetryTime(finishedTime, attemptNo),
                safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                finishedTime);
        log.warn("event=PAYMENT_MERCHANT_NOTIFY_ATTEMPT_END notifyId={} transactionId={} operationId={} attemptNo={} httpStatus={} success=false nextStatus={} exhausted={} durationMs={}",
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                attemptNo,
                result.httpStatus(),
                exhausted ? STATUS_CLOSED : STATUS_FAILED,
                exhausted,
                durationMillis(beginTime, finishedTime));
        return false;
    }

    /**
     * 完成 execute Http Notify 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private NotifyAttemptResult executeHttpNotify(TransactionMerchantNotificationDO task, LocalDateTime beginTime) {
        String targetUrl = resolveTargetUrl(task);
        if (!StringUtils.hasText(targetUrl)) {
            return new NotifyAttemptResult(false, null, null, "merchant callback url is empty");
        }
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
                    success ? null : "merchant callback http status " + response.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("event=PAYMENT_MERCHANT_NOTIFY_HTTP_FAILED notifyId={} transactionId={} operationId={} exceptionType={}",
                    task.getNotifyId(),
                    task.getTransactionId(),
                    task.getOperationId(),
                    exception.getClass().getSimpleName());
            return new NotifyAttemptResult(false, null, null, exception.getMessage());
        }
    }

/**
 * 写入或更新 insert Notify Log 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param task task 输入值，含义由调用方法名称和所属业务对象限定
 * @param attemptNo attempt No 输入值，含义由调用方法名称和所属业务对象限定
 * @param result result 输入值，含义由调用方法名称和所属业务对象限定
 * @param beginTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 * @param finishedTime 时间值，使用系统约定时区或调用方传入的业务时区解释
 */
    private void insertNotifyLog(TransactionMerchantNotificationDO task,
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
        notificationLogMapper.insertPhysical(
                physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, task.getTransactionDateTime()),
                logDO);
    }

    /**
     * 解析 resolve Target Url 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveTargetUrl(TransactionMerchantNotificationDO task) {
        if (!StringUtils.hasText(task.getNotifyConfigSnapshotJson())) {
            return null;
        }
        JSONObject snapshot = JsonUtils.parseObject(task.getNotifyConfigSnapshotJson(), JSONObject.class);
        return snapshot == null ? null : snapshot.getString("callbackUrl");
    }

    /**
     * 完成 next Retry Time 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param baseTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param attemptNo attempt No 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime nextRetryTime(LocalDateTime baseTime, int attemptNo) {
        long delaySeconds = BASE_RETRY_DELAY_SECONDS * Math.max(1, Math.min(attemptNo, 30));
        return baseTime.plusSeconds(delaySeconds);
    }

    /**
     * 完成 safe Max Retry 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private int safeMaxRetry(TransactionMerchantNotificationDO task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() <= 0 ? 1 : task.getMaxRetryCount();
    }

    /**
     * 完成 duration Millis 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param startTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    /**
     * 填充 fill Transaction Time 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param target target 输入值，含义由调用方法名称和所属业务对象限定
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
     * 完成 physical Table 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logicalTable logical Table 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
    }

    /**
     * 完成 safe Length 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param maxLength max Length 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 完成 elapsed Millis 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param startNanos start Nanos 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record NotifyAttemptResult(boolean success,
                                       Integer httpStatus,
                                       String responseBody,
                                       String errorMessage) {
    }
}
