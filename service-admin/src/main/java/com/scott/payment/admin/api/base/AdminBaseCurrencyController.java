package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
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
 * @classname : AdminBaseCurrencyController
 * @date : 2026-06-19 21:06
 * @email : scott_x@163.com
 * @description : 管理后台币种管理控制器
 * @status : create
 *
 * <p>币种管理接口入口，负责 ISO 4217 币种资料的参数接收、权限校验和 HTTP 映射，
 * 具体业务编排与数据处理由应用服务层完成。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCurrencyController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Currency 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/base/currencies")
public class AdminBaseCurrencyController {

    /**
     * 币种基础资料应用服务。
     */
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
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("base:currency:query")
    public CommonResult<IsoCurrencyDO> detail(@PathVariable("id") Long id) {
        return success(adminBaseCurrencyApplicationService.getCurrency(id));
    }

    /** 导出币种列表。 */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @GetMapping("/export")
    @RequiresPermission("base:currency:export")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.EXPORT, operation = "导出币种")
    public void export(HttpServletResponse response) {
        adminBaseCurrencyApplicationService.exportCurrencies(currentOperatorName(), response);
    }

    /** 新增币种。 */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param row 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("base:currency:add")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.CREATE, operation = "新增币种")
    public CommonResult<IsoCurrencyDO> create(@RequestBody IsoCurrencyDO row) {
        return success(adminBaseCurrencyApplicationService.createCurrency(row));
    }

    /** 修改币种。 */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("base:currency:edit")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑币种")
    public CommonResult<IsoCurrencyDO> update(@PathVariable("id") Long id, @RequestBody IsoCurrencyDO input) {
        return adminBaseCurrencyApplicationService.updateCurrency(id, input);
    }

    /** 切换币种状态。 */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:currency:changeStatus")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "切换币种状态")
    public CommonResult<IsoCurrencyDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        return adminBaseCurrencyApplicationService.updateStatus(id, body);
    }

    /** 逻辑删除币种。 */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("base:currency:remove")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.DELETE, operation = "删除币种")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminBaseCurrencyApplicationService.removeCurrency(id);
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
