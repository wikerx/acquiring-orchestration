package com.scott.payment.merchant.converter;

import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;
import com.scott.payment.merchant.entity.SysDictDataDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantDictConverter {

    MerchantDictConverter INSTANCE = Mappers.getMapper(MerchantDictConverter.class);

    DictDataResponse toResponse(SysDictDataDO entity);

    DictDataResponse toResponse(DictionaryOptionSnapshot snapshot);
}
