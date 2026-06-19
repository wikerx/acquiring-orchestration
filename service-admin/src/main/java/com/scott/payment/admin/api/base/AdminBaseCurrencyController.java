package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 币种管理控制器 — 提供 ISO 4217 币种的增删改查分页接口
 */
@RestController
@RequestMapping("/admin/base/currencies")
public class AdminBaseCurrencyController {

    private final AdminBaseCurrencyApplicationService adminBaseCurrencyApplicationService;

    /**
     * 创建币种管理控制器。
     *
     * @param adminBaseCurrencyApplicationService 币种应用服务
     */
    public AdminBaseCurrencyController(AdminBaseCurrencyApplicationService adminBaseCurrencyApplicationService) {
        this.adminBaseCurrencyApplicationService = adminBaseCurrencyApplicationService;
    }

    /** 分页查询币种列表。 */
    @GetMapping("/list")
    @RequiresPermission("base:currency:list")
    public CommonResult<PageResult<IsoCurrencyDO>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(adminBaseCurrencyApplicationService.pageCurrencies(pageNo, pageSize, keyword, status));
    }

    /** 查询单条币种详情。 */
    @GetMapping("/{id}")
    @RequiresPermission("base:currency:query")
    public CommonResult<IsoCurrencyDO> detail(@PathVariable("id") Long id) {
        return success(adminBaseCurrencyApplicationService.getCurrency(id));
    }

    /** 导出币种列表。 */
    @GetMapping("/export")
    @RequiresPermission("base:currency:export")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.EXPORT, operation = "导出币种")
    public CommonResult<List<IsoCurrencyDO>> export() {
        return success(adminBaseCurrencyApplicationService.exportCurrencies());
    }

    /** 新增币种。 */
    @PostMapping
    @RequiresPermission("base:currency:add")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.CREATE, operation = "新增币种")
    public CommonResult<IsoCurrencyDO> create(@RequestBody IsoCurrencyDO row) {
        return success(adminBaseCurrencyApplicationService.createCurrency(row));
    }

    /** 修改币种。 */
    @PutMapping("/{id}")
    @RequiresPermission("base:currency:edit")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑币种")
    public CommonResult<IsoCurrencyDO> update(@PathVariable("id") Long id, @RequestBody IsoCurrencyDO input) {
        return adminBaseCurrencyApplicationService.updateCurrency(id, input);
    }

    /** 切换币种状态。 */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:currency:changeStatus")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "切换币种状态")
    public CommonResult<IsoCurrencyDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        return adminBaseCurrencyApplicationService.updateStatus(id, body);
    }

    /** 逻辑删除币种。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:currency:remove")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.DELETE, operation = "删除币种")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminBaseCurrencyApplicationService.removeCurrency(id);
        return success(null);
    }
}
