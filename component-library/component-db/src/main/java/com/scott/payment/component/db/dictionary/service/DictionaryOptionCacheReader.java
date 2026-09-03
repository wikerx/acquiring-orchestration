package com.scott.payment.component.db.dictionary.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.dictionary.entity.SharedDictionaryDataDO;
import com.scott.payment.component.db.dictionary.mapper.SharedDictionaryDataMapper;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictionaryOptionCacheReader
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 跨系统启用数据字典下拉快照读取器，以有限 TTL 吸收高频表单查询并从主库可靠重建
 * @status : create
 */
@Service
public class DictionaryOptionCacheReader {

    /**
     * {@code NOT_DELETED}常量，统一 {@code DictionaryOptionCacheReader} 内部使用的配置值、状态码或协议字段。
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

    /** 公共数据字典数据访问组件。 */
    private final SharedDictionaryDataMapper dictionaryDataMapper;

    /**
     * 创建公共数据字典快照读取器。
     *
     * @param dictionaryDataMapper 公共数据字典 Mapper
     */
    public DictionaryOptionCacheReader(SharedDictionaryDataMapper dictionaryDataMapper) {
        this.dictionaryDataMapper = dictionaryDataMapper;
    }

    /**
     * 按字典类型和语言查询全部启用下拉项。
     *
     * @param dictType 字典类型
     * @param locale 语言区域
     * @return 按排序值和主键升序排列的启用快照
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.SYSTEM_DICT_OPTIONS,
            key = "T(com.scott.payment.component.db.dictionary.support.DictionaryOptionCacheKey)"
                    + ".of(#p0, #p1)",
            condition = "@dictionaryOptionCacheCondition.isCacheAllowed(#p0, #p1)"
    )
    public List<DictionaryOptionSnapshot> findEnabled(String dictType, String locale) {
        String normalizedType = dictType.trim();
        String normalizedLocale = locale.trim();
        return dictionaryDataMapper.selectList(Wrappers.<SharedDictionaryDataDO>lambdaQuery()
                        .eq(SharedDictionaryDataDO::getDictType, normalizedType)
                        .eq(SharedDictionaryDataDO::getLocale, normalizedLocale)
                        .eq(SharedDictionaryDataDO::getStatus, ENABLED)
                        .eq(SharedDictionaryDataDO::getDeleted, NOT_DELETED)
                        .orderByAsc(SharedDictionaryDataDO::getDictSort)
                        .orderByAsc(SharedDictionaryDataDO::getId))
                .stream()
                .map(this::toSnapshot)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 将数据库记录转换为不含管理审计字段的共享快照。 */
    private DictionaryOptionSnapshot toSnapshot(SharedDictionaryDataDO row) {
        DictionaryOptionSnapshot snapshot = new DictionaryOptionSnapshot();
        snapshot.setId(row.getId());
        snapshot.setDictType(row.getDictType());
        snapshot.setDictLabel(row.getDictLabel());
        snapshot.setDictValue(row.getDictValue());
        snapshot.setParentValue(row.getParentValue());
        snapshot.setLocale(row.getLocale());
        snapshot.setDictSort(row.getDictSort());
        snapshot.setListClass(row.getListClass());
        snapshot.setExtraJson(row.getExtraJson());
        snapshot.setIsDefault(row.getIsDefault());
        snapshot.setStatus(row.getStatus());
        return snapshot;
    }
}
