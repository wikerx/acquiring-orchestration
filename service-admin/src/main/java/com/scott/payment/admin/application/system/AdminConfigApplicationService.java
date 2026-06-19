package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置应用服务
 * @status : create
 */
@Service
public class AdminConfigApplicationService {

    private final AdminConfigService adminConfigService;

    /**
     * 创建后台系统配置应用服务。
     *
     * @param adminConfigService 系统配置领域服务
     */
    public AdminConfigApplicationService(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    /**
     * 保存系统配置。
     *
     * @param request 保存请求
     * @return 配置详情
     */
    public SysConfigDTO saveConfig(SysConfigSaveRequest request) {
        return adminConfigService.saveConfig(request);
    }

    /**
     * 按配置键查询系统配置。
     *
     * @param configKey 配置键
     * @return 配置详情
     */
    public SysConfigDTO getConfigByKey(String configKey) {
        return adminConfigService.getConfigByKey(configKey);
    }

    /**
     * 分页查询系统配置。
     *
     * @param request 查询条件
     * @return 配置分页结果
     */
    public PageResult<SysConfigDTO> pageConfigs(SysConfigQueryRequest request) {
        return adminConfigService.pageConfigs(request);
    }

    /**
     * 删除系统配置。
     *
     * @param configKey 配置键
     */
    public void deleteConfig(String configKey) {
        adminConfigService.deleteConfig(configKey);
    }
}
