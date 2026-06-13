package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigController
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/configs")
public class AdminConfigController {

    /**
     * 系统参数配置服务。
     */
    private final AdminConfigService configService;

    /**
     * 创建系统参数配置内部接口。
     *
     * @param configService 系统参数配置服务
     */
    public AdminConfigController(AdminConfigService configService) {
        this.configService = configService;
    }

    /**
     * 保存或更新系统参数配置。
     *
     * @param request 保存请求
     * @return 保存后的配置
     */
    @PostMapping
    @RequiresPermission("system:config:add")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.CREATE, operation = "新增系统参数配置")
    public CommonResult<SysConfigDTO> createConfig(@Valid @RequestBody SysConfigSaveRequest request) {
        return success(configService.saveConfig(request));
    }

    /**
     * 更新系统参数配置。
     *
     * @param configKey 配置键
     * @param request   保存请求
     * @return 保存后的配置
     */
    @PutMapping("/{configKey}")
    @RequiresPermission("system:config:edit")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.UPDATE, operation = "更新系统参数配置")
    public CommonResult<SysConfigDTO> updateConfig(@PathVariable("configKey") String configKey,
                                                   @Valid @RequestBody SysConfigSaveRequest request) {
        request.setConfigKey(configKey);
        return success(configService.saveConfig(request));
    }

    /**
     * 根据配置键查询系统参数配置。
     *
     * @param configKey 配置键
     * @return 系统参数配置
     */
    @GetMapping("/{configKey}")
    @RequiresPermission("system:config:query")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.QUERY, operation = "根据配置键查询系统参数配置")
    public CommonResult<SysConfigDTO> getConfig(@PathVariable("configKey") String configKey) {
        return success(configService.getConfigByKey(configKey));
    }

    /**
     * 按条件查询系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    @PostMapping("/search")
    @RequiresPermission("system:config:list")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.QUERY, operation = "分页查询系统参数配置列表")
    public CommonResult<PageResult<SysConfigDTO>> listConfigs(@RequestBody(required = false) SysConfigQueryRequest request) {
        return success(configService.pageConfigs(request));
    }

    /**
     * 软删除系统参数配置。
     *
     * @param configKey 配置键
     * @return 删除结果
     */
    @DeleteMapping("/{configKey}")
    @RequiresPermission("system:config:remove")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.DELETE, operation = "删除系统参数配置")
    public CommonResult<Void> deleteConfig(@PathVariable("configKey") String configKey) {
        configService.deleteConfig(configKey);
        return success();
    }

    /**
     * 导出系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    @PostMapping("/export")
    @RequiresPermission("system:config:export")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.EXPORT, operation = "导出系统参数配置")
    public CommonResult<PageResult<SysConfigDTO>> exportConfigs(@RequestBody(required = false) SysConfigQueryRequest request) {
        return success(configService.pageConfigs(request));
    }

    /**
     * 刷新系统参数缓存。
     *
     * @return 空响应
     */
    @PostMapping("/refresh-cache")
    @RequiresPermission("system:config:refresh")
    @OperationLog(moduleName = "系统配置", businessType = OperationTypeConstants.UPDATE, operation = "刷新系统参数缓存")
    public CommonResult<Void> refreshCache() {
        return success();
    }
}
