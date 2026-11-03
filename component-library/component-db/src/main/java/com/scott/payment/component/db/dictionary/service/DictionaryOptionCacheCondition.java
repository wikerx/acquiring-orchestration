package com.scott.payment.component.db.dictionary.service;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.dictionary.support.DictionaryOptionCacheKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 共享数据字典下拉缓存门禁，在字典变更事务至缓存删除完成期间阻止旧快照命中和回填
 */
@Slf4j
@Component
public class DictionaryOptionCacheCondition {

    /**
     * 缓存失效门禁；未启用 Redis 的最小运行环境中允许为空。
     */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建共享字典缓存门禁条件。
     *
     * @param invalidationGuardProvider 缓存失效门禁延迟提供器
     */
    public DictionaryOptionCacheCondition(
            ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider) {
        this.invalidationGuard = invalidationGuardProvider.getIfAvailable();
    }

    /**
     * 判断指定字典类型和语言的下拉快照是否允许访问缓存。
     *
     * <p>门禁明确处于 pending 或 Redis 状态未知时均绕过缓存，由读取器从主库重建权威快照。</p>
     *
     * @param dictType 字典类型编码
     * @param locale 语言区域
     * @return true 表示允许读取和回填缓存；false 表示必须绕过缓存
     */
    public boolean isCacheAllowed(String dictType, String locale) {
        if (invalidationGuard == null) {
            return true;
        }
        String businessKey = DictionaryOptionCacheKey.of(dictType, locale);
        try {
            return !invalidationGuard.isPending(PaymentCacheNames.SYSTEM_DICT_OPTIONS, businessKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "数据字典缓存门禁检查失败, event: DICTIONARY_OPTION_CACHE_GUARD_CHECK_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}
