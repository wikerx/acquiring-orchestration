package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.entity.SysConfigDO;
import com.scott.payment.admin.mapper.SysConfigMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigServiceImpl
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置服务实现
 * @status : create
 */
@Service
public class AdminConfigServiceImpl implements AdminConfigService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 默认启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 系统参数配置 Mapper。
     */
    private final SysConfigMapper sysConfigMapper;

    /**
     * 创建系统参数配置服务实现。
     *
     * @param sysConfigMapper 系统参数配置 Mapper
     */
    public AdminConfigServiceImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    /**
     * 保存或更新系统参数配置。
     *
     * @param request 系统参数配置保存请求
     * @return 保存后的配置
     */
    @Override
    public SysConfigDTO saveConfig(SysConfigSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        SysConfigDO entity = findActiveConfig(request.getConfigKey());
        if (entity == null) {
            entity = new SysConfigDO();
            entity.setConfigKey(request.getConfigKey());
            entity.setCreatedBy(request.getOperator());
            entity.setCreatedAt(now);
            entity.setDeleted(NOT_DELETED);
        }
        fillConfig(entity, request, now);
        if (entity.getId() == null) {
            sysConfigMapper.insert(entity);
        } else {
            sysConfigMapper.updateById(entity);
        }
        return toConfigDTO(entity);
    }

    /**
     * 根据配置键查询启用或停用的未删除配置。
     *
     * @param configKey 参数键名
     * @return 参数配置
     */
    @Override
    public SysConfigDTO getConfigByKey(String configKey) {
        SysConfigDO entity = findActiveConfig(configKey);
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), ApiResultEnum.NOT_FOUND.getMessage() + ":" + configKey);
        }
        return toConfigDTO(entity);
    }

    /**
     * 按条件查询系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    @Override
    public PageResult<SysConfigDTO> pageConfigs(SysConfigQueryRequest request) {
        SysConfigQueryRequest query = request == null ? new SysConfigQueryRequest() : request;
        Page<SysConfigDO> page = sysConfigMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildConfigQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(this::toConfigDTO).toList()
        );
    }

    /**
     * 构建系统参数配置查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysConfigDO> buildConfigQueryWrapper(SysConfigQueryRequest query) {
        return Wrappers.<SysConfigDO>lambdaQuery()
                .eq(SysConfigDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getConfigKey()), SysConfigDO::getConfigKey, query.getConfigKey())
                .eq(StringUtils.hasText(query.getConfigGroup()), SysConfigDO::getConfigGroup, query.getConfigGroup())
                .eq(query.getStatus() != null, SysConfigDO::getStatus, query.getStatus())
                .likeRight(StringUtils.hasText(query.getConfigName()), SysConfigDO::getConfigName, query.getConfigName())
                .orderByDesc(SysConfigDO::getUpdatedAt);
    }

    /**
     * 软删除指定配置。
     *
     * @param configKey 参数键名
     */
    @Override
    public void deleteConfig(String configKey) {
        SysConfigDO entity = findActiveConfig(configKey);
        if (entity == null) {
            return;
        }
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        sysConfigMapper.updateById(entity);
    }

    /**
     * 根据配置键查询未删除配置。
     *
     * @param configKey 参数键名
     * @return 配置实体
     */
    private SysConfigDO findActiveConfig(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), ApiResultEnum.PARAM_MISSING.getMessage() + ":configKey");
        }
        return sysConfigMapper.selectOne(
                Wrappers.<SysConfigDO>lambdaQuery()
                        .eq(SysConfigDO::getConfigKey, configKey)
                        .eq(SysConfigDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 将保存请求填充到配置实体。
     *
     * @param entity  配置实体
     * @param request 保存请求
     * @param now     当前时间
     */
    private void fillConfig(SysConfigDO entity, SysConfigSaveRequest request, LocalDateTime now) {
        entity.setConfigName(request.getConfigName());
        entity.setConfigValue(request.getConfigValue());
        entity.setValueType(request.getValueType());
        entity.setConfigGroup(request.getConfigGroup());
        entity.setSystemBuiltin(defaultIfNull(request.getSystemBuiltin(), 0));
        entity.setVisible(defaultIfNull(request.getVisible(), ENABLED));
        entity.setEncrypted(defaultIfNull(request.getEncrypted(), 0));
        entity.setStatus(defaultIfNull(request.getStatus(), ENABLED));
        entity.setRemark(request.getRemark());
        entity.setUpdatedBy(request.getOperator());
        entity.setUpdatedAt(now);
    }

    /**
     * 将配置实体转换为响应 DTO。
     *
     * @param entity 配置实体
     * @return 配置响应 DTO
     */
    private SysConfigDTO toConfigDTO(SysConfigDO entity) {
        SysConfigDTO dto = new SysConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigName(entity.getConfigName());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setValueType(entity.getValueType());
        dto.setConfigGroup(entity.getConfigGroup());
        dto.setSystemBuiltin(entity.getSystemBuiltin());
        dto.setVisible(entity.getVisible());
        dto.setEncrypted(entity.getEncrypted());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    /**
     * 获取非空整数值。
     *
     * @param value        入参值
     * @param defaultValue 默认值
     * @return 非空整数
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
