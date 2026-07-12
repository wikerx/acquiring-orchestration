package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseMccApplicationService;
import com.scott.payment.admin.dto.base.MccRequests;
import com.scott.payment.admin.dto.base.MccVO;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
 * @classname : AdminBaseMccController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Mcc 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/base/mcc")
public class AdminBaseMccController {

    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminBaseMccApplicationService adminBaseMccApplicationService;

    /**
     * 创建 MCC 管理控制器。
     *
     * @param adminBaseMccApplicationService MCC 管理应用服务
     */
    public AdminBaseMccController(AdminBaseMccApplicationService adminBaseMccApplicationService) {
        this.adminBaseMccApplicationService = adminBaseMccApplicationService;
    }

    /**
     * 查询 MCC 树。
     */
    @PostMapping("/tree")
    @RequiresPermission("base:mcc:tree:view")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.QUERY, operation = "查询 MCC 树")
    public CommonResult<List<MccVO.MccTreeNodeVO>> tree(@RequestBody(required = false) MccRequests.MccTreeQueryRequest request) {
        return success(adminBaseMccApplicationService.tree(request));
    }

    /**
     * 导出 MCC 编码。
     */
    @PostMapping("/tree/export")
    @RequiresPermission("base:mcc:tree:export")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.EXPORT, operation = "导出 MCC 编码")
    public void exportTree(@RequestBody(required = false) MccRequests.MccTreeQueryRequest request,
                           HttpServletResponse response) {
        adminBaseMccApplicationService.exportCodes(request, currentOperatorName(), response);
    }

    /**
     * 保存 MCC 分类。
     */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/category/add")
    @RequiresPermission("base:mcc:category:add")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.CREATE, operation = "新增 MCC 分类")
    public CommonResult<MccVO.MccTreeNodeVO> addCategory(@Valid @RequestBody MccRequests.MccCategorySaveRequest request) {
        request.setId(null);
        return success(adminBaseMccApplicationService.saveCategory(request));
    }

    /**
     * 编辑 MCC 分类。
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/category/edit")
    @RequiresPermission("base:mcc:category:edit")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 分类")
    public CommonResult<MccVO.MccTreeNodeVO> editCategory(@Valid @RequestBody MccRequests.MccCategorySaveRequest request) {
        return success(adminBaseMccApplicationService.saveCategory(request));
    }

    /**
     * 更新 MCC 分类状态。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/category/status")
    @RequiresPermission("base:mcc:category:status")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "更新 MCC 分类状态")
    public CommonResult<Void> categoryStatus(@Valid @RequestBody MccRequests.MccStatusUpdateRequest request) {
        adminBaseMccApplicationService.updateStatus(request);
        return success();
    }

    /**
     * 删除 MCC 分类。
     */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/category/delete")
    @RequiresPermission("base:mcc:category:delete")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.DELETE, operation = "删除 MCC 分类")
    public CommonResult<Void> deleteCategory(@Valid @RequestBody MccRequests.MccDeleteRequest request) {
        adminBaseMccApplicationService.deleteCategory(request);
        return success();
    }

    /**
     * 新增 MCC 编码。
     */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/code/add")
    @RequiresPermission("base:mcc:code:add")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.CREATE, operation = "新增 MCC 编码")
    public CommonResult<MccVO.MccCodeVO> addCode(@Valid @RequestBody MccRequests.MccCodeSaveRequest request) {
        request.setId(null);
        return success(adminBaseMccApplicationService.createCode(request));
    }

    /**
     * 编辑 MCC 编码。
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/code/edit")
    @RequiresPermission("base:mcc:code:edit")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 编码")
    public CommonResult<MccVO.MccCodeVO> editCode(@Valid @RequestBody MccRequests.MccCodeSaveRequest request) {
        return success(adminBaseMccApplicationService.updateCode(request));
    }

    /**
     * 查询 MCC 编码详情。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/code/detail")
    @RequiresPermission("base:mcc:code:view")
    public CommonResult<MccVO.MccCodeVO> codeDetail(@Valid @RequestBody MccRequests.MccIdRequest request) {
        return success(adminBaseMccApplicationService.getCode(request.getId()));
    }

    /**
     * 更新 MCC 编码状态。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/code/status")
    @RequiresPermission("base:mcc:code:status")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "更新 MCC 编码状态")
    public CommonResult<Void> codeStatus(@Valid @RequestBody MccRequests.MccStatusUpdateRequest request) {
        request.setNodeType("MCC_CODE");
        adminBaseMccApplicationService.updateStatus(request);
        return success();
    }

    /**
     * 删除 MCC 编码。
     */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/code/delete")
    @RequiresPermission("base:mcc:code:delete")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.DELETE, operation = "删除 MCC 编码")
    public CommonResult<Void> deleteCode(@Valid @RequestBody MccRequests.MccDeleteRequest request) {
        adminBaseMccApplicationService.deleteCode(request);
        return success();
    }

    /**
     * 导出 MCC 编码。
     */
    @PostMapping("/code/export")
    @RequiresPermission("base:mcc:code:export")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.EXPORT, operation = "导出 MCC 编码")
    public void exportCodes(@RequestBody(required = false) MccRequests.MccTreeQueryRequest request,
                            HttpServletResponse response) {
        adminBaseMccApplicationService.exportCodes(request, currentOperatorName(), response);
    }

    /**
     * 分页查询 MCC 风险策略。
     */
    @PostMapping("/policy/page")
    @RequiresPermission("base:mcc:policy:view")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.QUERY, operation = "分页查询 MCC 风险策略")
    public CommonResult<PageResult<MccVO.MccRiskPolicyVO>> pagePolicies(@RequestBody(required = false) MccRequests.MccRiskPolicyQueryRequest request) {
        return success(adminBaseMccApplicationService.pagePolicies(request));
    }

    /**
     * 查询 MCC 风险策略详情。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/policy/detail")
    @RequiresPermission("base:mcc:policy:view")
    public CommonResult<MccVO.MccRiskPolicyVO> policyDetail(@Valid @RequestBody MccRequests.MccIdRequest request) {
        return success(adminBaseMccApplicationService.getPolicyDetail(request.getId()));
    }

    /**
     * 新增 MCC 风险策略。
     */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/policy/add")
    @RequiresPermission("base:mcc:policy:add")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.CREATE, operation = "新增 MCC 风险策略")
    public CommonResult<List<MccVO.MccRiskPolicyVO>> addPolicy(@Valid @RequestBody MccRequests.MccRiskPolicySaveRequest request) {
        request.setId(null);
        return success(adminBaseMccApplicationService.createPolicies(request));
    }

    /**
     * 编辑 MCC 风险策略。
     */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/policy/edit")
    @RequiresPermission("base:mcc:policy:edit")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 风险策略")
    public CommonResult<MccVO.MccRiskPolicyVO> editPolicy(@Valid @RequestBody MccRequests.MccRiskPolicySaveRequest request) {
        return success(adminBaseMccApplicationService.updatePolicy(request));
    }

    /**
     * 更新 MCC 风险策略状态。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/policy/status")
    @RequiresPermission("base:mcc:policy:status")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.UPDATE, operation = "更新 MCC 风险策略状态")
    public CommonResult<Void> policyStatus(@Valid @RequestBody MccRequests.MccStatusUpdateRequest request) {
        adminBaseMccApplicationService.updatePolicyStatus(request);
        return success();
    }

    /**
     * 删除 MCC 风险策略。
     */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/policy/delete")
    @RequiresPermission("base:mcc:policy:delete")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.DELETE, operation = "删除 MCC 风险策略")
    public CommonResult<Void> deletePolicy(@Valid @RequestBody MccRequests.MccDeleteRequest request) {
        adminBaseMccApplicationService.deletePolicy(request);
        return success();
    }

    /**
     * 查询 MCC 概览。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/overview")
    @RequiresPermission("base:mcc:overview:view")
    public CommonResult<MccVO.MccOverviewVO> overview() {
        return success(adminBaseMccApplicationService.overview());
    }

    /**
     * 查询 MCC 页面下拉选项。
     */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/options")
    @RequiresPermission("base:mcc:view")
    public CommonResult<Map<String, Object>> options() {
        return success(adminBaseMccApplicationService.options());
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
