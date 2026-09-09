package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminClearingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculationOptionsResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
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
 * @classname : AdminClearingController
 * @date : 2026-09-01 22:50
 * @email : scott_x@163.com
 * @description : Admin 清分记录查询和人工处置入口；由权限注解与操作日志保护，业务编排和可信身份解析统一下沉应用层。
 * @status : update
 */
@RestController
@RequestMapping("/admin/clearing/records")
public class AdminClearingController {

    private final AdminClearingApplicationService applicationService;

    public AdminClearingController(AdminClearingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询管理数据范围内的清分记录。
     *
     * @param request 清分状态、交易、商户和时间范围条件
     * @return 清分记录分页
     */
    @PostMapping("/search")
    @RequiresPermission("clearing:record:list")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分记录")
    public CommonResult<PageResult<Summary>> search(@RequestBody SearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 按交易号和交易时间定位清分详情。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易时间，用于定位交易物理季度
     * @return 清分详情
     */
    @GetMapping("/{transactionId}")
    @RequiresPermission("clearing:record:detail")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分详情")
    public CommonResult<DetailResponse> detail(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.detail(transactionId, transactionDateTime));
    }

    /**
     * 查询指定商户费用模板可用于重算的已发布版本。
     *
     * @param merchantId 商户号，仍需经过 Admin 数据范围校验
     * @param feePlanId 费用模板主键
     * @return 可选费用版本
     */
    @GetMapping("/recalculation-options")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY, operation = "查询清分重算费用版本")
    public CommonResult<RecalculationOptionsResponse> recalculationOptions(
            @RequestParam("merchantId") String merchantId,
            @RequestParam("feePlanId") Long feePlanId) {
        return success(applicationService.recalculationOptions(merchantId, feePlanId));
    }

    /**
     * 请求清分服务幂等重试指定交易。
     *
     * @param transactionId 平台交易号
     * @param request 人工原因和请求键
     * @return 最新清分状态
     */
    @PostMapping("/{transactionId}/retry")
    @RequiresPermission("clearing:record:retry")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "人工重试清分")
    public CommonResult<CommandResponse> retry(@PathVariable("transactionId") String transactionId,
                                               @RequestBody ActionRequest request) {
        return success(applicationService.retry(transactionId, request));
    }

    /**
     * 将指定清分记录升级到人工复核流程。
     *
     * @param transactionId 平台交易号
     * @param request 人工原因和请求键
     * @return 最新清分状态
     */
    @PostMapping("/{transactionId}/review")
    @RequiresPermission("clearing:record:review")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "升级人工复核")
    public CommonResult<CommandResponse> review(@PathVariable("transactionId") String transactionId,
                                                @RequestBody ActionRequest request) {
        return success(applicationService.review(transactionId, request));
    }

    /**
     * 使用指定已发布费用版本重算尚未进入结算的清分事实。
     *
     * @param transactionId 平台交易号
     * @param request 目标费用版本、原因和请求键
     * @return 最新清分状态
     */
    @PostMapping("/{transactionId}/recalculate")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "重算未结算清分")
    public CommonResult<CommandResponse> recalculate(@PathVariable("transactionId") String transactionId,
                                                     @RequestBody RecalculateRequest request) {
        return success(applicationService.recalculate(transactionId, request));
    }

    /**
     * 对明确选择且尚未结算的清分记录逐笔提交幂等重算。
     *
     * @param request 交易选择、费用版本、原因和批量请求键
     * @return 成功、失败及逐笔结果摘要
     */
    @PostMapping("/recalculate-batch")
    @RequiresPermission("clearing:record:recalculate")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.UPDATE, operation = "批量重算未结算清分")
    public CommonResult<RecalculateBatchResponse> recalculateBatch(
            @RequestBody RecalculateBatchRequest request) {
        return success(applicationService.batchRecalculate(request));
    }
}
