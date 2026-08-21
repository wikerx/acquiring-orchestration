package com.scott.payment.component.db.email.service;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.email.support.EmailTemplateCacheKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EnabledEmailTemplateCacheCondition
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 已启用邮件模板缓存门禁条件，在模板变更窗口内阻止旧快照命中或重新回填
 * @status : create
 */
@Slf4j
@Component
public class EnabledEmailTemplateCacheCondition {

    /** 缓存失效门禁；未启用 Redis 的最小运行环境中允许为空。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建邮件模板缓存门禁条件。
     *
     * @param invalidationGuardProvider 缓存失效门禁延迟提供器
     */
    public EnabledEmailTemplateCacheCondition(
            ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider) {
        this.invalidationGuard = invalidationGuardProvider.getIfAvailable();
    }

    /**
     * 判断指定模板快照是否允许访问缓存。
     *
     * <p>门禁明确处于 pending 或 Redis 状态未知时均绕过缓存，由读取器从主库获取权威数据。</p>
     *
     * @param templateCode 模板编码
     * @param localeCode 语言区域
     * @return true 表示允许读取和回填缓存；false 表示必须绕过缓存
     */
    public boolean isCacheAllowed(String templateCode, String localeCode) {
        if (invalidationGuard == null) {
            return true;
        }
        try {
            return !invalidationGuard.isPending(
                    PaymentCacheNames.EMAIL_TEMPLATE_ENABLED,
                    EmailTemplateCacheKey.of(templateCode, localeCode)
            );
        } catch (RuntimeException exception) {
            log.warn("读取邮件模板缓存门禁失败，templateCode: {}，locale: {}，异常类型: {}",
                    templateCode, localeCode, exception.getClass().getSimpleName());
            return false;
        }
    }
}
