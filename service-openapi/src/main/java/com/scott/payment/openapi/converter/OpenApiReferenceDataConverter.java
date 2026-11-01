package com.scott.payment.openapi.converter;

import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;
import org.mapstruct.Mapper;

/**
 * 基础数据公共检索结果到商户 OpenAPI 响应对象的无业务规则字段转换器
 */
@Mapper(componentModel = "spring")
public interface OpenApiReferenceDataConverter {

    /**
     * 转换 IP 归属检索结果。
     *
     * @param result 公共检索结果
     * @return 商户 OpenAPI 响应
     */
    IpLookupVO toIpLookupVO(IpLookupResult result);

    /**
     * 转换卡 BIN 归属检索结果。
     *
     * @param result 公共检索结果
     * @return 商户 OpenAPI 响应
     */
    CardBinLookupVO toCardBinLookupVO(CardBinLookupResult result);
}
