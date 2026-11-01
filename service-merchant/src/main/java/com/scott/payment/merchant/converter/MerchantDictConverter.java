package com.scott.payment.merchant.converter;

import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;
import com.scott.payment.merchant.entity.SysDictDataDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * Merchant Dict Converter 转换组件，位于 商户后台服务，在接口模型、领域对象、数据库记录或消息载荷之间复制并规范化字段。
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MerchantDictConverter {

    MerchantDictConverter INSTANCE = Mappers.getMapper(MerchantDictConverter.class);

    /**
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param entity 商户端可见的数据字典持久化记录
     * @return 构造、转换或解析后的业务值
     */
    DictDataResponse toResponse(SysDictDataDO entity);

    /**
     * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 转换过程不改变来源对象的业务状态；敏感字段仅保留目标模型所需的最小集合。
     * </p>
     * @param snapshot 动作受理时冻结的不可变配置快照，用于计算复现和完整性校验
     * @return 构造、转换或解析后的业务值
     */
    DictDataResponse toResponse(DictionaryOptionSnapshot snapshot);
}
