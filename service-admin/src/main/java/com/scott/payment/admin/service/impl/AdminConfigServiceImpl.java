package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.converter.ConfigConverter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigServiceImpl
 * @date : 2026-06-19 21:54
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置领域服务实现
 * @status : create
 *
 * <p>负责系统参数配置的持久化规则、唯一键校验与软删除处理，不承担接口协议适配或权限控制逻辑。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminConfigServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Config Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 系统参数配置对象转换器。
     */
    private final ConfigConverter configConverter;

    /**
     * 创建系统参数配置服务实现。
     *
     * @param sysConfigMapper 系统参数配置 Mapper
     * @param configConverter 系统参数配置对象转换器
     */
    public AdminConfigServiceImpl(SysConfigMapper sysConfigMapper, ConfigConverter configConverter) {
        this.sysConfigMapper = sysConfigMapper;
        this.configConverter = configConverter;
    }

    /**
     * 保存或更新系统参数配置。
     *
     * @param request 系统参数配置保存请求
     * @return 保存后的配置
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
        return configConverter.toDTO(entity);
    }

    /**
     * 根据配置键查询启用或停用的未删除配置。
     *
     * @param configKey 参数键名
     * @return 参数配置
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param configKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysConfigDTO getConfigByKey(String configKey) {
        SysConfigDO entity = findActiveConfig(configKey);
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), ApiResultEnum.NOT_FOUND.getMessage() + ":" + configKey);
        }
        return configConverter.toDTO(entity);
    }

    /**
     * 按配置键集合查询启用的未删除配置值。
     *
     * @param configKeys 参数键名集合
     * @return 参数键名与参数值映射
     */
    @Override
    public Map<String, String> enabledConfigValues(Set<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        sysConfigMapper.selectList(Wrappers.<SysConfigDO>lambdaQuery()
                        .in(SysConfigDO::getConfigKey, configKeys)
                        .eq(SysConfigDO::getStatus, ENABLED)
                        .eq(SysConfigDO::getDeleted, NOT_DELETED))
                .forEach(row -> {
                    if (StringUtils.hasText(row.getConfigValue())) {
                        values.put(row.getConfigKey(), row.getConfigValue().trim());
                    }
                });
        return values;
    }

    /**
     * 按条件查询系统参数配置列表。
     *
     * @param request 查询条件
     * @return 系统参数配置列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
                page.getRecords().stream().map(configConverter::toDTO).toList()
        );
    }

    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysConfigDTO> listConfigs(SysConfigQueryRequest request) {
        SysConfigQueryRequest query = request == null ? new SysConfigQueryRequest() : request;
        return sysConfigMapper.selectList(buildConfigQueryWrapper(query))
                .stream()
                .map(configConverter::toDTO)
                .toList();
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
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param configKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
        assertHttpBaseUrl(request.getConfigKey(), request.getConfigValue());
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
     * 平台访问地址类配置必须保存为 HTTP(S) base URL，避免邮件模板生成不可点击或不安全的链接。
     *
     * @param configKey 参数键名
     * @param configValue 参数值
     */
    private void assertHttpBaseUrl(String configKey, String configValue) {
        if (!SystemConfigKeys.HTTP_BASE_URL_KEYS.contains(configKey)) {
            return;
        }
        if (!StringUtils.hasText(configValue)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "平台访问地址不能为空");
        }
        String value = configValue.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "平台访问地址必须以 http:// 或 https:// 开头");
        }
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
