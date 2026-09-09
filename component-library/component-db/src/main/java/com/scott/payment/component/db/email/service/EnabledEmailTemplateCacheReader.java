package com.scott.payment.component.db.email.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.email.entity.SharedEmailTemplateDO;
import com.scott.payment.component.db.email.mapper.SharedEmailTemplateMapper;
import com.scott.payment.component.db.email.model.EnabledEmailTemplateSnapshot;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EnabledEmailTemplateCacheReader
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 已启用邮件模板有限期快照读取器，缓存未命中时从主库重建
 * @status : create
 */
@Service
public class EnabledEmailTemplateCacheReader {

    /**
     * {@code NOT_DELETED}常量，统一 {@code EnabledEmailTemplateCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;

    /** 公共邮件模板数据访问组件。 */
    private final SharedEmailTemplateMapper templateMapper;

    /**
     * 创建已启用邮件模板快照读取器。
     *
     * @param templateMapper 公共邮件模板 Mapper
     */
    public EnabledEmailTemplateCacheReader(SharedEmailTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /**
     * 按模板编码和语言查询已启用模板。
     *
     * @param templateCode 模板编码
     * @param localeCode 语言区域
     * @return 已启用模板快照；不存在时返回 null 且不缓存空值
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.EMAIL_TEMPLATE_ENABLED,
            key = "T(com.scott.payment.component.db.email.support.EmailTemplateCacheKey)"
                    + ".of(#p0, #p1)",
            condition = "@enabledEmailTemplateCacheCondition.isCacheAllowed(#p0, #p1)",
            unless = "#result == null"
    )
    public EnabledEmailTemplateSnapshot findEnabled(String templateCode, String localeCode) {
        SharedEmailTemplateDO row = templateMapper.selectOne(
                Wrappers.<SharedEmailTemplateDO>lambdaQuery()
                        .eq(SharedEmailTemplateDO::getTemplateCode,
                                templateCode.trim().toUpperCase(Locale.ROOT))
                        .eq(SharedEmailTemplateDO::getLocale, localeCode.trim())
                        .eq(SharedEmailTemplateDO::getStatus, ENABLED)
                        .eq(SharedEmailTemplateDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        return row == null ? null : toSnapshot(row);
    }

    /** 将数据库记录转换为不包含发件账号密钥的模板快照。 */
    private EnabledEmailTemplateSnapshot toSnapshot(SharedEmailTemplateDO row) {
        EnabledEmailTemplateSnapshot snapshot = new EnabledEmailTemplateSnapshot();
        snapshot.setId(row.getId());
        snapshot.setTemplateCode(row.getTemplateCode());
        snapshot.setTemplateName(row.getTemplateName());
        snapshot.setAppCode(row.getAppCode());
        snapshot.setSceneCode(row.getSceneCode());
        snapshot.setLocale(row.getLocale());
        snapshot.setSubjectTemplate(row.getSubjectTemplate());
        snapshot.setContentType(row.getContentType());
        snapshot.setContentTemplate(row.getContentTemplate());
        snapshot.setVariableSchema(row.getVariableSchema());
        snapshot.setSensitiveVariableNames(row.getSensitiveVariableNames());
        return snapshot;
    }
}
