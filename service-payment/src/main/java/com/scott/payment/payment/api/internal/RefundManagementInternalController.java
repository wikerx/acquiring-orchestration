package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.payment.application.RefundManagementApplicationService;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundDetailResponse;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundSearchResponse;
import org.springframework.format.annotation.DateTimeFormat;
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
 * @classname : RefundManagementInternalController
 * @date : 2026-08-06 15:50
 * @email : scott_x@163.com
 * @description : Payment 内部退款管理只读接口，供 Admin 和 Merchant 在统一过滤口径下查询退款与撤销动作。
 * @status : create
 */
@RestController
@RequestMapping("/internal/payment/refunds")
public class RefundManagementInternalController {

    private final RefundManagementApplicationService applicationService;

    /** @param applicationService 退款管理应用服务 */
    public RefundManagementInternalController(RefundManagementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询退款分页和统计。 */
    @PostMapping("/search")
    public CommonResult<RefundSearchResponse> search(@RequestBody(required = false) RefundQuery query) {
        return success(applicationService.search(query));
    }

    /** 使用真实退款分片时间查询详情。 */
    @GetMapping("/{transactionId}")
    public CommonResult<RefundDetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime,
            @RequestParam(value = "merchantId", required = false) String merchantId) {
        return success(applicationService.detail(transactionId, transactionDateTime, merchantId));
    }
}
