package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCountryApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCountryController
 * @date : 2026-06-19 21:05
 * @email : scott_x@163.com
 * @description : 管理后台国家地区管理控制器
 * @status : create
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
    public CommonResult<PageResult<IsoCountryDO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "continentCode", required = false) String continentCode,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(adminBaseCountryApplicationService.pageCountries(pageNo, pageSize, keyword, continentCode, status));
    }

    /** 查询单条国家/地区详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("base:country:query")
    public CommonResult<IsoCountryDO> detail(@PathVariable("id") Long id) {
        return success(adminBaseCountryApplicationService.getCountry(id));
    }

    /** 导出国家/地区列表。 */
    @GetMapping("/export")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT, operation = "导出国家地区")
    public void export(HttpServletResponse response) {
        adminBaseCountryApplicationService.exportCountries(currentOperatorName(), response);
    }

    /** 新增国家/地区。 */
    @PostMapping
    @RequiresPermission("base:country:add")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.CREATE, operation = "新增国家地区")
    public CommonResult<IsoCountryDO> create(@RequestBody IsoCountryDO row) {
        return success(adminBaseCountryApplicationService.createCountry(row));
    }

    /** 修改国家/地区。 */
    @PutMapping("/{id}")
    @RequiresPermission("base:country:edit")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "编辑国家地区")
    public CommonResult<IsoCountryDO> update(@PathVariable("id") Long id, @RequestBody IsoCountryDO input) {
        return adminBaseCountryApplicationService.updateCountry(id, input);
    }

    /** 切换国家/地区状态。 */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:country:changeStatus")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "切换国家地区状态")
    public CommonResult<IsoCountryDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        return adminBaseCountryApplicationService.updateStatus(id, body);
    }

    /** 逻辑删除国家/地区。 */
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
