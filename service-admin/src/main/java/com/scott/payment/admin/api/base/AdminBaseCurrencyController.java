package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseCurrencyApplicationService;
import com.scott.payment.admin.dto.base.IsoCurrencyDTOs.Response;
import com.scott.payment.admin.dto.base.IsoCurrencyDTOs.SaveRequest;
import com.scott.payment.admin.dto.base.IsoStatusUpdateRequest;
import com.scott.payment.component.core.iso.IsoCurrencyPresentationInfo;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台币种管理控制器
 *
 * <p>币种管理接口入口，负责 ISO 4217 币种资料的参数接收、权限校验和 HTTP 映射，
 * 具体业务编排与数据处理由应用服务层完成。</p>
 */

@RestController
@RequestMapping("/admin/base/currencies")
public class AdminBaseCurrencyController {

    /**
     * 币种基础资料应用服务。
     */
    private final AdminBaseCurrencyApplicationService adminBaseCurrencyApplicationService;

    /**
     * 登录后各管理端页面共用的币种展示字典。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建币种管理控制器。
     *
     * @param adminBaseCurrencyApplicationService 币种应用服务
     * @param isoDictionaryService ISO 字典查询服务
     */
    public AdminBaseCurrencyController(AdminBaseCurrencyApplicationService adminBaseCurrencyApplicationService,
                                       IsoDictionaryService isoDictionaryService) {
        this.adminBaseCurrencyApplicationService = adminBaseCurrencyApplicationService;
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 登录后的管理端页面可读取币种展示信息，无需拥有币种维护权限。
     *
     * @return 当前启用币种的展示信息
     */
    @GetMapping("/presentations")
    public CommonResult<List<IsoCurrencyPresentationInfo>> presentations() {
        return success(isoDictionaryService.listCurrencyPresentations());
    }

    /**
     * 分页查询币种列表。
     *
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @param keyword 币种代码或名称关键字
     * @param status 启停状态
     * @return 币种分页结果
     */
    @GetMapping("/list")
    @RequiresPermission("base:currency:list")
    public CommonResult<PageResult<Response>> list(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(adminBaseCurrencyApplicationService.pageCurrencies(pageNo, pageSize, keyword, status));
    }

    /**
     * 查询单条币种详情。
     *
     * @param id 币种记录主键
     * @return 币种详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("base:currency:query")
    public CommonResult<Response> detail(@PathVariable("id") Long id) {
        return success(adminBaseCurrencyApplicationService.getCurrency(id));
    }

    /**
     * 导出币种列表。
     *
     * @param response 写入导出文件的 HTTP 响应
     */
    @GetMapping("/export")
    @RequiresPermission("base:currency:export")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.EXPORT, operation = "导出币种")
    public void export(HttpServletResponse response) {
        adminBaseCurrencyApplicationService.exportCurrencies(currentOperatorName(), response);
    }

    /**
     * 新增币种。
     *
     * @param request 币种保存请求
     * @return 新增后的币种资料
     */
    @PostMapping
    @RequiresPermission("base:currency:add")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.CREATE, operation = "新增币种")
    public CommonResult<Response> create(@Valid @RequestBody SaveRequest request) {
        return success(adminBaseCurrencyApplicationService.createCurrency(request));
    }

    /**
     * 修改币种。
     *
     * @param id 币种记录主键
     * @param request 币种保存请求
     * @return 更新后的币种资料
     */
    @PutMapping("/{id}")
    @RequiresPermission("base:currency:edit")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "编辑币种")
    public CommonResult<Response> update(@PathVariable("id") Long id,
                                         @Valid @RequestBody SaveRequest request) {
        return adminBaseCurrencyApplicationService.updateCurrency(id, request);
    }

    /**
     * 切换币种状态。
     *
     * @param id 币种记录主键
     * @param request 目标启停状态
     * @return 更新后的币种资料
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:currency:changeStatus")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.UPDATE, operation = "切换币种状态")
    public CommonResult<Response> changeStatus(@PathVariable("id") Long id,
                                               @Valid @RequestBody IsoStatusUpdateRequest request) {
        return adminBaseCurrencyApplicationService.updateStatus(id, request);
    }

    /**
     * 逻辑删除币种。
     *
     * @param id 币种记录主键
     * @return 无业务数据的成功响应
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
