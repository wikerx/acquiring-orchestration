package com.scott.payment.admin.api;

import com.scott.payment.admin.annotation.AdminOperationLog;
import com.scott.payment.admin.constant.AdminOperationTypeConstants;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @AdminOperationLog(moduleName = "系统配置", businessType = AdminOperationTypeConstants.UPDATE, operation = "保存或更新系统参数配置")
    public CommonResult<SysConfigDTO> saveConfig(@Valid @RequestBody SysConfigSaveRequest request) {
        return CommonResult.success(configService.saveConfig(request));
    }

    /**
     * 根据配置键查询系统参数配置。
     *
     * @param configKey 配置键
     * @return 系统参数配置
     */
    @GetMapping("/{configKey}")
    @AdminOperationLog(moduleName = "系统配置", businessType = AdminOperationTypeConstants.QUERY, operation = "根据配置键查询系统参数配置")
    public CommonResult<SysConfigDTO> getConfig(@PathVariable String configKey) {
        return CommonResult.success(configService.getConfigByKey(configKey));
    }

    /**
     * 按条件查询系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    @PostMapping("/search")
    @AdminOperationLog(moduleName = "系统配置", businessType = AdminOperationTypeConstants.QUERY, operation = "查询系统参数配置列表")
    public CommonResult<List<SysConfigDTO>> listConfigs(@RequestBody(required = false) SysConfigQueryRequest request) {
        return CommonResult.success(configService.listConfigs(request));
    }

    /**
     * 软删除系统参数配置。
     *
     * @param configKey 配置键
     * @return 删除结果
     */
    @DeleteMapping("/{configKey}")
    @AdminOperationLog(moduleName = "系统配置", businessType = AdminOperationTypeConstants.DELETE, operation = "删除系统参数配置")
    public CommonResult<Void> deleteConfig(@PathVariable String configKey) {
        configService.deleteConfig(configKey);
        return CommonResult.success();
    }
}
