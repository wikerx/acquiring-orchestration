package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminClearingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplaySubmitRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTierPeriodReplayController
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : Admin 阶梯期间重放申请和双人复核入口；不提供浏览器直接执行、跳序或覆盖已结算事实的能力。
 * @status : create
 */
@RestController
@RequestMapping("/admin/clearing/tier-period-replays")
public class AdminTierPeriodReplayController {

    private final AdminClearingApplicationService applicationService;

    public AdminTierPeriodReplayController(AdminClearingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 提交商户、不可变费用版本和月份闭包的重放申请。 */
    @PostMapping
    @RequiresPermission("clearing:tier-period-replay:submit")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.CREATE,
            operation = "提交阶梯期间重放申请")
    public CommonResult<TierPeriodReplayResponse> submit(
            @RequestBody TierPeriodReplaySubmitRequest request) {
        return success(applicationService.submitTierPeriodReplay(request));
    }

    /** 由不同操作人批准或拒绝；批准后服务端自动推进。 */
    @PostMapping("/{replayNo}/review")
    @RequiresPermission("clearing:tier-period-replay:review")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.AUDIT,
            operation = "复核阶梯期间重放申请")
    public CommonResult<TierPeriodReplayResponse> review(
            @PathVariable("replayNo") String replayNo,
            @RequestBody TierPeriodReplayReviewRequest request) {
        return success(applicationService.reviewTierPeriodReplay(replayNo, request));
    }
}
