package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.transaction.MerchantRefundApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRefundController
 * @date : 2026-08-06 16:10
 * @email : scott_x@163.com
 * @description : 商户端退款管理只读接口，只允许当前认证商户查询自身退款，不注册勾兑异常能力。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/transactions/refunds")
public class MerchantRefundController {

    private final MerchantRefundApplicationService applicationService;

    /** @param applicationService 商户退款应用服务 */
    public MerchantRefundController(MerchantRefundApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询当前商户退款分页和统计。 */
    @PostMapping("/search")
    @RequiresPermission("merchant:transaction:refund:list")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.QUERY, operation = "查询商户退款列表")
    public CommonResult<RefundSearchResponse> search(@RequestBody(required = false) RefundQuery query) {
        return success(applicationService.search(currentMerchantId(), query));
    }

    /** 查询当前商户退款详情。 */
    @GetMapping("/{transactionId}")
    @RequiresPermission("merchant:transaction:refund:detail")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.QUERY, operation = "查询商户退款详情")
    public CommonResult<RefundDetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(currentMerchantId(), transactionId, transactionDateTime));
    }

    /** 导出当前认证商户的退款记录。 */
    @PostMapping("/export")
    @RequiresPermission("merchant:transaction:refund:export")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.EXPORT, operation = "导出商户退款列表")
    public void export(@RequestBody(required = false) RefundQuery query, HttpServletResponse response) {
        applicationService.export(currentMerchantId(), query, currentOperatorName(), response);
    }

    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "unknown";
        }
        return StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
    }
}
