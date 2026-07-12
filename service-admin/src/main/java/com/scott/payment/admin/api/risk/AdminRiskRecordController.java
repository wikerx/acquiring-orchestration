package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskRecordController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 风控记录查询接口，位于 service-admin 接口层，仅用于管理端查看交易风控评估记录和命中明细。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk/record")
public class AdminRiskRecordController {

    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    /**
     * 创建风控记录查询接口。
     *
     * @param riskManagementApplicationService 风控管理应用服务
     */
    public AdminRiskRecordController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 分页查询风控评估记录。
     *
     * @param request 查询条件
     * @return 风控评估记录分页结果
     */
    @PostMapping("/evaluations/page")
    @RequiresPermission("risk:record:evaluation:list")
    public CommonResult<PageResult<Map<String, Object>>> pageEvaluations(@RequestBody(required = false) RiskDTOs.EvaluationQueryRequest request) {
        return success(riskManagementApplicationService.pageEvaluations(request));
    }

    /**
     * 查询风控评估命中明细。
     *
     * @param riskRecordNo 风控记录号
     * @return 命中明细列表
     */
    @GetMapping("/evaluations/{riskRecordNo}/hits")
    @RequiresPermission("risk:record:evaluation:detail")
    public CommonResult<List<Map<String, Object>>> evaluationHits(@PathVariable("riskRecordNo") String riskRecordNo) {
        return success(riskManagementApplicationService.evaluationHits(riskRecordNo));
    }
}
