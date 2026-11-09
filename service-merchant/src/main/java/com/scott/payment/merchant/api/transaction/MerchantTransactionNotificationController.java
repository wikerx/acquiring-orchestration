package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.transaction.MerchantNotificationApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRecord;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationRetryRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 商户端交易通知记录接口，只允许认证商户查询自身通知任务和脱敏投递日志。
 */
@RestController
@RequestMapping("/merchant/transactions/notifications")
public class MerchantTransactionNotificationController {

    private final MerchantNotificationApplicationService applicationService;

    /**
     * 创建商户交易通知记录控制器。
     *
     * @param applicationService 商户通知查询应用服务
     */
    public MerchantTransactionNotificationController(MerchantNotificationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询当前认证商户的交易通知任务。
     *
     * @param query 查询和分页条件
     * @return 当前商户的通知任务分页
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:transaction:notification:list")
    @OperationLog(moduleName = "交易通知记录", businessType = OperationTypeConstants.QUERY,
            operation = "查询商户交易通知记录")
    public CommonResult<PageResult<NotificationRecord>> search(
            @RequestBody(required = false) NotificationQuery query) {
        return success(applicationService.page(currentMerchantId(), query));
    }

    /**
     * 查询当前认证商户的一条通知任务及其投递尝试。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 脱敏通知任务与投递日志
     */
    @GetMapping("/{notifyId}")
    @RequiresPermission("merchant:transaction:notification:detail")
    @OperationLog(moduleName = "交易通知记录", businessType = OperationTypeConstants.QUERY,
            operation = "查询商户交易通知详情")
    public CommonResult<NotificationDetailResponse> detail(
            @PathVariable("notifyId") String notifyId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS") LocalDateTime transactionDateTime) {
        return success(applicationService.detail(currentMerchantId(), notifyId, transactionDateTime));
    }

    /**
     * 分页查询当前认证商户的一条通知任务的投递尝试。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页记录数，最大 100
     * @return 当前商户可见的脱敏投递日志分页
     */
    @GetMapping("/{notifyId}/delivery-logs")
    @RequiresPermission("merchant:transaction:notification:detail")
    @OperationLog(moduleName = "交易通知记录", businessType = OperationTypeConstants.QUERY,
            operation = "分页查询商户通知投递日志")
    public CommonResult<PageResult<DeliveryLog>> deliveryLogs(
            @PathVariable("notifyId") String notifyId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS") LocalDateTime transactionDateTime,
            @RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return success(applicationService.pageDeliveryLogs(
                currentMerchantId(), notifyId, transactionDateTime, pageNo, pageSize));
    }

    /**
     * 受理当前认证商户的一次通知人工重发请求。
     *
     * <p>成功通知也允许商户主动重发；仍处于自动投递计划或正在处理的任务由应用层拒绝。请求只写入
     * 可靠 MQ Outbox，不在商户接口线程直接访问回调地址，也不修改支付交易状态。</p>
     *
     * @param request 通知任务号、真实分片时间和客户端幂等请求号
     * @return 已受理的稳定 MQ 事件号
     */
    @PostMapping("/retry")
    @RequiresPermission("merchant:transaction:notification:retry")
    @OperationLog(moduleName = "交易通知记录", businessType = OperationTypeConstants.UPDATE,
            operation = "商户人工重发交易通知")
    public CommonResult<String> retry(@Valid @RequestBody NotificationRetryRequest request) {
        InternalAuthAccount account = currentAccount();
        return success(applicationService.retry(
                account.getMerchantId(), currentOperator(account), request));
    }

    /**
     * 从可信认证上下文读取当前商户号，缺失时在进入查询层前拒绝请求。
     *
     * @return 当前认证商户号
     */
    private String currentMerchantId() {
        return currentAccount().getMerchantId();
    }

    /**
     * 从线程认证上下文读取当前商户账号，缺失商户边界时立即拒绝。
     *
     * @return 已绑定商户号的认证账号
     */
    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account;
    }

    /**
     * 生成不包含姓名、邮箱等个人信息的商户操作人审计摘要。
     *
     * @param account 当前认证商户账号
     * @return 登录账号优先、账号主键兜底的审计摘要
     */
    private String currentOperator(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getLoginAccount())) {
            return "merchant-account:" + account.getLoginAccount().trim();
        }
        return "merchant-account:" + account.getAccountId();
    }
}
