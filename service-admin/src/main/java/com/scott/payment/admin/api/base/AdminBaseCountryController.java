package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCountryApplicationService;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.Response;
import com.scott.payment.admin.dto.base.IsoCountryDTOs.SaveRequest;
import com.scott.payment.admin.dto.base.IsoStatusUpdateRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台国家地区管理控制器
 *
 * <p>国家/地区管理接口入口，负责 ISO 3166 国家地区资料的参数接收、权限校验和 HTTP 映射，
 * 具体业务编排与数据处理由应用服务层完成。</p>
 */
@RestController
@RequestMapping("/admin/base/countries")
public class AdminBaseCountryController {

    /**
     * 国家地区基础资料应用服务。
     */
    private final AdminBaseCountryApplicationService adminBaseCountryApplicationService;

    /**
     * 创建国家/地区管理控制器。
     *
     * @param adminBaseCountryApplicationService 国家地区应用服务
     */
    public AdminBaseCountryController(AdminBaseCountryApplicationService adminBaseCountryApplicationService) {
        this.adminBaseCountryApplicationService = adminBaseCountryApplicationService;
    }

    /**
     * 分页查询国家/地区列表，支持按关键字、大洲筛选。
     */
    @GetMapping("/list")
    @RequiresPermission("base:country:list")
    public CommonResult<PageResult<Response>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "continentCode", required = false) String continentCode,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(adminBaseCountryApplicationService.pageCountries(pageNo, pageSize, keyword, continentCode, status));
    }

    /**
     * 查询指定 ISO 国家/地区基础资料。
     *
     * @param id 国家/地区记录主键
     * @return 国家/地区详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("base:country:query")
    public CommonResult<Response> detail(@PathVariable("id") Long id) {
        return success(adminBaseCountryApplicationService.getCountry(id));
    }

    /**
     * 导出当前权限范围内的国家/地区资料。
     *
     * @param response 写入导出文件的 HTTP 响应
     */
    @GetMapping("/export")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT, operation = "导出国家地区")
    public void export(HttpServletResponse response) {
        adminBaseCountryApplicationService.exportCountries(currentOperatorName(), response);
    }

    /**
     * 创建 ISO 国家/地区资料，代码唯一性由应用服务校验。
     *
     * @param request 国家/地区保存请求
     * @return 创建后的记录
     */
    @PostMapping
    @RequiresPermission("base:country:add")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.CREATE, operation = "新增国家地区")
    public CommonResult<Response> create(@Valid @RequestBody SaveRequest request) {
        return success(adminBaseCountryApplicationService.createCountry(request));
    }

    /**
     * 更新指定国家/地区资料并触发 ISO 字典缓存失效。
     *
     * @param id 国家/地区记录主键
     * @param request 更新内容
     * @return 更新后的记录
     */
    @PutMapping("/{id}")
    @RequiresPermission("base:country:edit")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "编辑国家地区")
    public CommonResult<Response> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SaveRequest request) {
        return adminBaseCountryApplicationService.updateCountry(id, request);
    }

    /**
     * 切换国家/地区启停状态并刷新 ISO 字典缓存。
     *
     * @param id 国家/地区记录主键
     * @param request 目标启停状态
     * @return 更新后的记录
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:country:changeStatus")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "切换国家地区状态")
    public CommonResult<Response> changeStatus(@PathVariable("id") Long id,
                                               @Valid @RequestBody IsoStatusUpdateRequest request) {
        return adminBaseCountryApplicationService.updateStatus(id, request);
    }

    /**
     * 删除指定国家/地区资料并触发 ISO 字典缓存失效。
     *
     * @param id 国家/地区记录主键
     * @return 无业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:country:remove")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.DELETE, operation = "删除国家地区")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminBaseCountryApplicationService.removeCountry(id);
        return success(null);
    }

    /**
     * 获取当前操作人名称，用于写入导出元信息。
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
