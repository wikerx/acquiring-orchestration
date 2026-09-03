package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementProfileApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementProfileController
 * @date : 2026-09-01 22:50
 * @email : scott_x@163.com
 * @description : Admin 结算档案本地查询与调度参数维护入口；页面不直连 settlement 服务，更新操作受权限、数据范围、审计和乐观锁保护。
 * @status : update
 */
@RestController
@RequestMapping("/admin/settlement/profiles")
public class AdminSettlementProfileController {

    private final AdminSettlementProfileApplicationService applicationService;

    public AdminSettlementProfileController(AdminSettlementProfileApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询管理数据范围内的结算档案。
     *
     * @param request 商户、账户、币种、处理模式、状态和分页条件
     * @return 结算档案分页
     */
    @PostMapping("/search")
    @RequiresPermission("settlement:profile:list")
    @OperationLog(moduleName = "结算档案", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算档案")
    public CommonResult<PageResult<ProfileSummary>> search(@RequestBody ProfileSearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 查询管理数据范围内的结算档案详情。
     *
     * @param settlementProfileNo 结算档案编号
     * @return 结算档案详情
     */
    @GetMapping("/{settlementProfileNo}")
    @RequiresPermission("settlement:profile:detail")
    @OperationLog(moduleName = "结算档案", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算档案详情")
    public CommonResult<ProfileSummary> detail(@PathVariable("settlementProfileNo") String settlementProfileNo) {
        return success(applicationService.detail(settlementProfileNo));
    }

    /**
     * 使用乐观锁修改后续批次的处理模式、业务时区和日切时间，不重写历史批次事实。
     *
     * @param settlementProfileNo 结算档案编号
     * @param request 新调度参数、变更原因和期望版本
     * @return 更新后的结算档案
     */
    @PutMapping("/{settlementProfileNo}")
    @RequiresPermission("settlement:profile:update")
    @OperationLog(moduleName = "结算档案", businessType = OperationTypeConstants.UPDATE,
            operation = "修改结算档案调度参数")
    public CommonResult<ProfileSummary> update(@PathVariable("settlementProfileNo") String settlementProfileNo,
                                               @RequestBody ProfileUpdateRequest request) {
        return success(applicationService.update(settlementProfileNo, request));
    }
}
