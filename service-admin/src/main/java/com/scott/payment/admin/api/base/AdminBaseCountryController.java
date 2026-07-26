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

    @GetMapping("/{id}")
    @RequiresPermission("base:country:query")
    /**
     * 完成 detail 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<IsoCountryDO> detail(@PathVariable("id") Long id) {
        return success(adminBaseCountryApplicationService.getCountry(id));
    }

    @GetMapping("/export")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT, operation = "导出国家地区")
    /**
     * 完成 export 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param response response 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void export(HttpServletResponse response) {
        adminBaseCountryApplicationService.exportCountries(currentOperatorName(), response);
    }

    @PostMapping
    @RequiresPermission("base:country:add")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.CREATE, operation = "新增国家地区")
    /**
     * 完成 create 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<IsoCountryDO> create(@RequestBody IsoCountryDO row) {
        return success(adminBaseCountryApplicationService.createCountry(row));
    }

    @PutMapping("/{id}")
    @RequiresPermission("base:country:edit")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "编辑国家地区")
    /**
     * 写入或更新 update 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param input input 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<IsoCountryDO> update(@PathVariable("id") Long id, @RequestBody IsoCountryDO input) {
        return adminBaseCountryApplicationService.updateCountry(id, input);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("base:country:changeStatus")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.UPDATE, operation = "切换国家地区状态")
    /**
     * 完成 change Status 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param body body 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<IsoCountryDO> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, Integer> body) {
        return adminBaseCountryApplicationService.updateStatus(id, body);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("base:country:remove")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.DELETE, operation = "删除国家地区")
    /**
     * 完成 remove 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
