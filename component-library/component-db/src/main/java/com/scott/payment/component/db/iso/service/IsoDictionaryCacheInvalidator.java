package com.scott.payment.component.db.iso.service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryCacheInvalidator
 * @date : 2026-07-30 21:30
 * @email : scott_x@163.com
 * @description : ISO 字典缓存精确失效契约，由管理端在国家、币种或地区币种映射持久化成功后调用，确保新旧实例都不读取陈旧字典
 * @status : create
 */
public interface IsoDictionaryCacheInvalidator {

    /**
     * 删除新旧国家地区全量缓存。
     *
     * <p>该方法只用于国家资料或默认币种映射发生真实变更后的业务失效，
     * 不用于迁移时批量清理历史 Key。</p>
     */
    void evictCountries();

    /**
     * 删除新旧币种全量缓存。
     *
     * <p>该方法只用于币种资料发生真实变更后的业务失效，
     * 不用于迁移时批量清理历史 Key。</p>
     */
    void evictCurrencies();
}
