package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;
import com.scott.payment.data.service.MerchantCallbackSecurityMaterialProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 从主库读取当前启用的商户 JWT 密钥和响应公钥，不经过交易分片数据源。 */
@Service
@DS(DataSourceName.MASTER)
public class JdbcMerchantCallbackSecurityMaterialProvider implements MerchantCallbackSecurityMaterialProvider {

    /** 启用状态值。 */
    private static final int ENABLED = 1;
    /** 未删除状态值。 */
    private static final int NOT_DELETED = 0;

    /** 商户 JWT 密钥数据访问器，只查询当前有效密钥。 */
    private final BaseMerchantJwtKeyMapper jwtKeyMapper;
    /** 商户响应公钥数据访问器，只查询当前启用公钥。 */
    private final BaseMerchantResponseKeyMapper responseKeyMapper;

    /**
     * 创建安全材料提供器。
     *
     * @param jwtKeyMapper 商户 JWT 密钥数据访问器
     * @param responseKeyMapper 商户响应公钥数据访问器
     */
    public JdbcMerchantCallbackSecurityMaterialProvider(BaseMerchantJwtKeyMapper jwtKeyMapper,
                                                         BaseMerchantResponseKeyMapper responseKeyMapper) {
        this.jwtKeyMapper = jwtKeyMapper;
        this.responseKeyMapper = responseKeyMapper;
    }

    /**
     * 从主库加载商户当前有效密钥；结果仅限当前回调请求使用。
     *
     * @param merchantId 商户号
     * @return 商户回调 JWT 密钥和响应公钥
     */
    @Override
    public MerchantCallbackSecurityMaterial load(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        BaseMerchantJwtKeyDO jwtKey = jwtKeyMapper.selectOne(Wrappers.<BaseMerchantJwtKeyDO>query()
                .select("merchant_key")
                .eq("merchant_id", merchantId)
                .eq("enabled", ENABLED)
                .eq("deleted", NOT_DELETED)
                .and(wrapper -> wrapper.isNull("effective_time").or().le("effective_time", now))
                .and(wrapper -> wrapper.isNull("expire_time").or().gt("expire_time", now))
                .orderByDesc("effective_time")
                .last("LIMIT 1"));
        BaseMerchantResponseKeyDO responseKey = responseKeyMapper.selectOne(
                Wrappers.<BaseMerchantResponseKeyDO>query()
                        .select("public_key_x509_base64")
                        .eq("merchant_id", merchantId)
                        .eq("enabled", ENABLED)
                        .eq("deleted", NOT_DELETED)
                        .orderByDesc("gmt_modified")
                        .last("LIMIT 1"));
        if (jwtKey == null
                || responseKey == null
                || !StringUtils.hasText(jwtKey.getMerchantKey())
                || !StringUtils.hasText(responseKey.getPublicKeyX509Base64())) {
            throw new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                    "merchant callback security material is not configured");
        }
        return new MerchantCallbackSecurityMaterial(jwtKey.getMerchantKey(), responseKey.getPublicKeyX509Base64());
    }
}
