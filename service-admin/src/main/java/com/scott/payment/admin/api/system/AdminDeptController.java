package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminDeptApplicationService;
import com.scott.payment.admin.dto.SysDeptDTO;
import com.scott.payment.admin.dto.system.DeptDTOs.Response;
import com.scott.payment.admin.dto.system.DeptDTOs.SaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台部门管理控制器
 *
 * <p>部门树、详情和维护请求统一通过
 * {@link AdminDeptApplicationService} 编排，Controller 保持为薄入口层。</p>
 */
@RestController
@RequestMapping("/admin/system/dept")
public class AdminDeptController {

    private final AdminDeptApplicationService adminDeptApplicationService;

    /**
     * 创建部门管理控制器。
     *
     * @param adminDeptApplicationService 部门应用服务
     */
    public AdminDeptController(AdminDeptApplicationService adminDeptApplicationService) {
        this.adminDeptApplicationService = adminDeptApplicationService;
    }

    /**
     * 查询部门树。
     *
     * @return 部门树形列表
     */
    @GetMapping("/tree")
    @RequiresPermission("system:dept:list")
    public CommonResult<List<SysDeptDTO>> tree() {
        return success(adminDeptApplicationService.tree());
    }

    /**
     * 查询部门详情。
     *
     * @param id 部门主键 ID
     * @return 部门详情响应
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:dept:query")
    public CommonResult<Response> detail(@PathVariable("id") Long id) {
        return success(adminDeptApplicationService.getDept(id));
    }

    /**
     * 导出部门树。
     */
    @GetMapping("/export")
    @RequiresPermission("system:dept:export")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.EXPORT, operation = "导出部门")
    public void export(HttpServletResponse response) {
        adminDeptApplicationService.exportDepts(currentOperatorName(), response);
    }

    /**
     * 新增部门。
     *
     * @param request 部门保存请求
     * @return 新增后的部门响应
     */
    @PostMapping
    @RequiresPermission("system:dept:add")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.CREATE, operation = "新增部门")
    public CommonResult<Response> create(@Valid @RequestBody SaveRequest request) {
        return success(adminDeptApplicationService.createDept(request));
    }

    /**
     * 修改部门。
     *
     * @param id 部门主键 ID
     * @param request 部门保存请求
     * @return 更新后的部门响应
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:dept:edit")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.UPDATE, operation = "修改部门")
    public CommonResult<Response> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SaveRequest request) {
        return success(adminDeptApplicationService.updateDept(id, request));
    }

    /**
     * 删除部门（逻辑删除）。
     *
     * @param id 部门主键 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:remove")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.DELETE, operation = "删除部门")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminDeptApplicationService.removeDept(id);
        return success();
    }

    /**
     * 获取当前操作人名称，用于写入导出文件元信息。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        com.scott.payment.component.core.auth.InternalAuthAccount account =
                com.scott.payment.component.core.auth.InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
