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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseCountryController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Country 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("base:country:query")
    public CommonResult<IsoCountryDO> detail(@PathVariable("id") Long id) {
        return success(adminBaseCountryApplicationService.getCountry(id));
    }

    /** 导出国家/地区列表。 */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @GetMapping("/export")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT, operation = "导出国家地区")
    public void export(HttpServletResponse response) {
        adminBaseCountryApplicationService.exportCountries(currentOperatorName(), response);
    }

    /** 新增国家/地区。 */
    /**
     * 创建或保存基础数据数据，保持请求校验、默认值和审计字段一致。
     * @param row 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("base:country:add")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.CREATE, operation = "新增国家地区")
    public CommonResult<IsoCountryDO> create(@RequestBody IsoCountryDO row) {
        return success(adminBaseCountryApplicationService.createCountry(row));
    }

    /** 修改国家/地区。 */
    /**
     * 更新基础数据数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param input 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("base:country:edit")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "编辑国家地区")
    public CommonResult<IsoCountryDO> update(@PathVariable("id") Long id, @RequestBody IsoCountryDO input) {
        return adminBaseCountryApplicationService.updateCountry(id, input);
    }

    /** 切换国家/地区状态。 */
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("base:country:changeStatus")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "切换国家地区状态")
    public CommonResult<IsoCountryDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        return adminBaseCountryApplicationService.updateStatus(id, body);
    }

    /** 逻辑删除国家/地区。 */
    /**
     * 删除基础数据数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
