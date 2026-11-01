package com.scott.payment.merchant.converter;

import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;
import com.scott.payment.merchant.entity.SysDictDataDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * 商户字典转换器，将字典实体转换为门户查询响应。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantDictConverter {

    MerchantDictConverter INSTANCE = Mappers.getMapper(MerchantDictConverter.class);

    /**
     * 将商户可见的字典实体转换为查询响应。
     */
    DictDataResponse toResponse(SysDictDataDO entity);

    /**
     * 将已发布字典快照转换为查询响应。
     */
    DictDataResponse toResponse(DictionaryOptionSnapshot snapshot);
}
