package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigService
 * @date : 2026-06-19 21:52
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置领域服务
 * @status : create
 *
 * <p>负责系统参数配置的核心领域规则，包括配置查询、保存更新和软删除，不处理接口协议适配。</p>
 */
public interface AdminConfigService {

    /**
     * 保存或更新系统参数配置。
     *
     * @param request 系统参数配置保存请求
     * @return 保存后的配置
     */
    SysConfigDTO saveConfig(SysConfigSaveRequest request);

    /**
     * 根据配置键查询启用或停用的未删除配置。
     *
     * @param configKey 参数键名
     * @return 参数配置
     */
    SysConfigDTO getConfigByKey(String configKey);

    /**
     * 按条件查询系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    PageResult<SysConfigDTO> pageConfigs(SysConfigQueryRequest request);

    /**
     * 软删除指定配置。
     *
     * @param configKey 参数键名
     */
    void deleteConfig(String configKey);
}
