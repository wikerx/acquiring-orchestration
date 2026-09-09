package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseRegionCurrencyApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
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
 * @classname : AdminBaseRegionCurrencyController
 * @date : 2026-06-19 21:07
 * @email : scott_x@163.com
 * @description : 管理后台地区币种配置控制器
 * @status : create
 *
 * <p>地区币种配置接口入口，负责国家/地区与默认币种关联关系的参数接收、权限校验和 HTTP 映射，
 * 具体业务编排与映射数据组装由应用服务层完成。</p>
 */

@RestController
@RequestMapping("/admin/base/region-currencies")
public class AdminBaseRegionCurrencyController {

    /**
     * 地区币种配置应用服务。
     */
    private final AdminBaseRegionCurrencyApplicationService adminBaseRegionCurrencyApplicationService;

    /**
     * 创建地区币种配置控制器。
     *
     * @param adminBaseRegionCurrencyApplicationService 地区币种配置应用服务
     */
    public AdminBaseRegionCurrencyController(AdminBaseRegionCurrencyApplicationService adminBaseRegionCurrencyApplicationService) {
        this.adminBaseRegionCurrencyApplicationService = adminBaseRegionCurrencyApplicationService;
    }

    /** 分页查询国家/地区与币种的关联列表。 */
    @GetMapping("/list")
    @RequiresPermission("base:countryCurrency:list")
    public CommonResult<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "continentCode", required = false) String continentCode) {
        return success(adminBaseRegionCurrencyApplicationService.pageRegionCurrencies(pageNo, pageSize, keyword, continentCode));
    }

    /** 查询国家/地区与币种关联详情。 */

    @GetMapping("/{id}")
    @RequiresPermission("base:countryCurrency:query")
    public CommonResult<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return adminBaseRegionCurrencyApplicationService.getRegionCurrency(id);
    }

    /** 导出国家/地区与币种关联列表。 */

    @GetMapping("/export")
    @RequiresPermission("base:countryCurrency:export")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.EXPORT, operation = "导出地区币种配置")
    public void export(HttpServletResponse response) {
        adminBaseRegionCurrencyApplicationService.exportRegionCurrencies(currentOperatorName(), response);
    }

    /** 新增国家/地区默认币种关联。 */

    @PostMapping
    @RequiresPermission("base:countryCurrency:add")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.CREATE, operation = "新增地区币种配置")
    public CommonResult<Void> createCurrency(@RequestBody Map<String, String> body) {
        return adminBaseRegionCurrencyApplicationService.createRegionCurrency(body);
    }

    /** 更新国家/地区的默认币种。 */

    @PutMapping("/{id}")
    @RequiresPermission("base:countryCurrency:edit")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.UPDATE, operation = "更新地区币种")
    public CommonResult<Void> updateCurrency(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> body) {
        return adminBaseRegionCurrencyApplicationService.updateRegionCurrency(id, body);
    }

    /** 删除国家/地区默认币种关联。 */

    @DeleteMapping("/{id}")
    @RequiresPermission("base:countryCurrency:remove")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.DELETE, operation = "删除地区币种配置")
    public CommonResult<Void> removeCurrency(@PathVariable("id") Long id) {
        return adminBaseRegionCurrencyApplicationService.removeRegionCurrency(id);
    }

    /** 切换国家/地区关联状态。 */

    @PutMapping("/{id}/status")
    @RequiresPermission("base:countryCurrency:changeStatus")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.UPDATE, operation = "切换地区币种配置状态")
    public CommonResult<Void> changeStatus(@PathVariable("id") Long id,
                                           @RequestBody Map<String, Integer> body) {
        return adminBaseRegionCurrencyApplicationService.updateStatus(id, body);
    }

    /**
     * 获取当前操作人名称，用于补齐导出元信息。
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
