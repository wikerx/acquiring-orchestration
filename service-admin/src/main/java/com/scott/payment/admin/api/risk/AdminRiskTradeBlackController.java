package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskTradeBlackController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 系统交易加黑接口，位于 service-admin 接口层，仅维护管理端交易加黑记录和解除操作。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk/trade-black")
public class AdminRiskTradeBlackController {

    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    public AdminRiskTradeBlackController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 分页查询系统交易加黑记录。
     *
     * @param request 查询条件
     * @return 交易加黑分页结果
     */
    @PostMapping("/page")
    @RequiresPermission("risk:tradeBlack:system:list")
    public CommonResult<PageResult<Map<String, Object>>> page(@RequestBody(required = false) RiskDTOs.TradeBlackQueryRequest request) {
        return success(riskManagementApplicationService.pageTradeBlack(request));
    }

    /**
     * 新增系统交易加黑。
     *
     * @param request 保存请求
     * @return 空结果
     */
    @PostMapping
    @RequiresPermission("risk:tradeBlack:system:add")
    @OperationLog(moduleName = "收单风控-系统交易加黑", businessType = OperationTypeConstants.CREATE, operation = "新增系统交易加黑")
    public CommonResult<Void> create(@Valid @RequestBody RiskDTOs.TradeBlackSaveRequest request) {
        riskManagementApplicationService.createTradeBlack(request);
        return success();
    }

    /**
     * 解除系统交易加黑。
     *
     * @param id     交易加黑记录ID
     * @param reason 解除原因
     * @return 空结果
     */
    @PutMapping("/{id}/release")
    @RequiresPermission("risk:tradeBlack:system:release")
    @OperationLog(moduleName = "收单风控-系统交易加黑", businessType = OperationTypeConstants.UPDATE, operation = "解除系统交易加黑")
    public CommonResult<Void> release(@PathVariable("id") Long id,
                                      @RequestParam(value = "reason", required = false) String reason) {
        riskManagementApplicationService.releaseTradeBlack(id, reason);
        return success();
    }
}
