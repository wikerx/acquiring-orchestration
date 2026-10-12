package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.db.reference.cache.IpLookupCacheReader;
import com.scott.payment.component.db.reference.model.CardBrandRuleMatcher;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataCacheReader
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 商户 IP 查询缓存与 BIN 结果适配；BIN 使用支付共享的 11 位缓存、从库回源和品牌兜底规则。
 * @status : create
 */
@Service
public class OpenApiReferenceDataCacheReader {

    private final IpLookupCacheReader ipLookupCacheReader;
    private final CardBinCacheReader cardBinCacheReader;

    public OpenApiReferenceDataCacheReader(IpLookupCacheReader ipLookupCacheReader,
                                           CardBinCacheReader cardBinCacheReader) {
        this.ipLookupCacheReader = ipLookupCacheReader;
        this.cardBinCacheReader = cardBinCacheReader;
    }

    /**
     * 查询精确 IP 归属，并通过支付公共组件共享命中与合法未命中缓存。
     * <p>等价的 IP 写法共用 SHA-256 摘要字段；一次 Lua 同时完成逻辑过期清理和读取。
     * 缓存值绑定当前 SLAVE 路由快照版本，版本变化后旧值即使删除失败也不会复用。
     * 缓存不可用时使用本地路由快照并仅查询一次 IP 动态数据表；非法地址、分片配置错误
     * 和数据库异常均不写入缓存。</p>
     *
     * @param ipAddress IPv4 或 IPv6 字面量，不接受域名和 CIDR
     * @return 当前有效的归属结果，合法地址未命中时返回 matched=false
     * @throws IllegalArgumentException IP 字面量不合法时抛出
     */
    public IpLookupResult lookupIp(String ipAddress) {
        return ipLookupCacheReader.lookup(ipAddress);
    }

    /**
     * 与支付侧共用 11 位 BIN 查询策略和缓存：短 BIN 右补零、长输入截取前 11 位。
     * <p>缓存只保存数据库真实命中。数据库未命中后，使用支付侧公开 IIN 规则
     * 补充品牌，但 matched 仍为 false，发卡行和国家等归属字段仍为空。
     * 超过 11 位的原始输入不进入缓存或响应。数据库异常继续上抛，不伪装成正常未命中。</p>
     *
     * @param cardBin 至少 6 位纯数字，可能包含完整卡号；不得记录原文
     * @return 当前有效归属结果，未命中时返回 matched=false 和规则识别的品牌
     * @throws IllegalArgumentException BIN 不足 6 位或含非数字字符时抛出
     */
    public CardBinLookupResult lookupCardBin(String cardBin) {
        String prefix = CardBinCacheReader.toElevenDigitPrefix(cardBin);
        CardBinLookupResult databaseResult = cardBinCacheReader.findByPrefix(prefix).result();
        // 品牌规则只补充本次商户响应，不能污染共享缓存中的数据库命中状态。
        String brand = Boolean.TRUE.equals(databaseResult.matched())
                ? databaseResult.cardBrand() : CardBrandRuleMatcher.resolve(prefix);
        String responseBin = cardBin.length() <= 11 ? cardBin : prefix;
        return new CardBinLookupResult(databaseResult.matched(), responseBin,
                databaseResult.binLength(), brand, databaseResult.cardSubBrand(),
                databaseResult.cardType(), databaseResult.cardLevel(),
                databaseResult.issuerCountryName(), databaseResult.issuerCountryAlpha2(),
                databaseResult.issuerCountryAlpha3(), databaseResult.issuerCountryNumeric(),
                databaseResult.issuerBank());
    }

}
