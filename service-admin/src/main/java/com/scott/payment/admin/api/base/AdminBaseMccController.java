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

@RestController
@RequestMapping("/admin/base/mcc")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseMccController
 * @date : 2026-06-27 16:49
 * @email : scott_x@163.com
 * @description : AdminBaseMccController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminBaseMccController {

    /**
     * admin Base Mcc Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
    @PostMapping("/category/edit")
    @RequiresPermission("base:mcc:category:edit")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 分类")
    public CommonResult<MccVO.MccTreeNodeVO> editCategory(@Valid @RequestBody MccRequests.MccCategorySaveRequest request) {
        return success(adminBaseMccApplicationService.saveCategory(request));
    }

    /**
     * 更新 MCC 分类状态。
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
    @PostMapping("/code/edit")
    @RequiresPermission("base:mcc:code:edit")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 编码")
    public CommonResult<MccVO.MccCodeVO> editCode(@Valid @RequestBody MccRequests.MccCodeSaveRequest request) {
        return success(adminBaseMccApplicationService.updateCode(request));
    }

    /**
     * 查询 MCC 编码详情。
     */
    @PostMapping("/code/detail")
    @RequiresPermission("base:mcc:code:view")
    public CommonResult<MccVO.MccCodeVO> codeDetail(@Valid @RequestBody MccRequests.MccIdRequest request) {
        return success(adminBaseMccApplicationService.getCode(request.getId()));
    }

    /**
     * 更新 MCC 编码状态。
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
    @PostMapping("/policy/detail")
    @RequiresPermission("base:mcc:policy:view")
    public CommonResult<MccVO.MccRiskPolicyVO> policyDetail(@Valid @RequestBody MccRequests.MccIdRequest request) {
        return success(adminBaseMccApplicationService.getPolicyDetail(request.getId()));
    }

    /**
     * 新增 MCC 风险策略。
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
    @PostMapping("/policy/edit")
    @RequiresPermission("base:mcc:policy:edit")
    @OperationLog(moduleName = "MCC 风险策略", businessType = OperationTypeConstants.UPDATE, operation = "编辑 MCC 风险策略")
    public CommonResult<MccVO.MccRiskPolicyVO> editPolicy(@Valid @RequestBody MccRequests.MccRiskPolicySaveRequest request) {
        return success(adminBaseMccApplicationService.updatePolicy(request));
    }

    /**
     * 更新 MCC 风险策略状态。
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
    @PostMapping("/overview")
    @RequiresPermission("base:mcc:overview:view")
    public CommonResult<MccVO.MccOverviewVO> overview() {
        return success(adminBaseMccApplicationService.overview());
    }

    /**
     * 查询 MCC 页面下拉选项。
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
