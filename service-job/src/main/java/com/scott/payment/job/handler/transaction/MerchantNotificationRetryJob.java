package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.client.payment.dto.PaymentMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.dto.transaction.MerchantNotificationRetryRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryJob
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 商户通知补偿任务，位于 service-job 任务处理层，按 transaction_date_time 定位分表并触发 service-payment 重试到期商户通知。
 * @status : create
 */
@Component
public class MerchantNotificationRetryJob implements JobHandler {

    /**
     * 任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "MERCHANT_NOTIFICATION_RETRY";

    /**
     * 处理器编码。
     */
    public static final String HANDLER_CODE = "merchantNotificationRetry";

    /**
     * DEFAULT LIMIT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int DEFAULT_LIMIT = 100;

    /**
     * MAX LIMIT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_LIMIT = 500;

    /**
     * payment Internal Client 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建商户通知补偿任务处理器。
     *
     * @param paymentInternalClient service-payment 内部补偿客户端
     */
    public MerchantNotificationRetryJob(PaymentInternalClient paymentInternalClient) {
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 返回处理器注册描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "商户通知补偿重试",
                "transaction",
                "扫描到期商户通知任务并调用支付核心执行补偿重试"
        );
    }

    /**
     * 执行商户通知补偿。
     *
     * @param context 任务执行上下文
     * @return 执行结果，包含每个分表时间点的成功通知数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        MerchantNotificationRetryRequest request = context == null ? null : context.parseParams(MerchantNotificationRetryRequest.class);
        if (request == null) {
            request = new MerchantNotificationRetryRequest();
        }
        int limit = normalizeLimit(request.getLimit());
        List<LocalDateTime> transactionDateTimes = resolveTransactionDateTimes(request);
        Map<String, Integer> result = new LinkedHashMap<>();
        int totalSuccessCount = 0;
        for (LocalDateTime transactionDateTime : transactionDateTimes) {
            PaymentMerchantNotificationNotifyDueClientRequestDTO clientRequestDTO =
                    new PaymentMerchantNotificationNotifyDueClientRequestDTO();
            clientRequestDTO.setTransactionDateTime(transactionDateTime);
            clientRequestDTO.setLimit(limit);
            Integer successCount = paymentInternalClient.notifyDueMerchantNotifications(clientRequestDTO);
            int safeSuccessCount = successCount == null ? 0 : successCount;
            totalSuccessCount += safeSuccessCount;
            result.put(transactionDateTime.toString(), safeSuccessCount);
        }
        return JobExecuteResult.success("merchant notification retry finished, successCount=" + totalSuccessCount, result);
    }

    /**
     * 解析 resolve Transaction Date Times 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 解析或查询得到的业务值
     */
    private List<LocalDateTime> resolveTransactionDateTimes(MerchantNotificationRetryRequest request) {
        if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
            return request.getTransactionDateTimes();
        }
        if (request.getTransactionDateTime() != null) {
            return List.of(request.getTransactionDateTime());
        }
        return List.of(LocalDateTime.now());
    }

    /**
     * 标准化 normalize Limit 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
