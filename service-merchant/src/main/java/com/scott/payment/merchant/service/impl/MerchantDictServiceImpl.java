package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.component.db.dictionary.service.DictionaryOptionCacheReader;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataQuery;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;
import com.scott.payment.merchant.entity.SysDictDataDO;
import com.scott.payment.merchant.mapper.SysDictDataMapper;
import com.scott.payment.merchant.service.MerchantDictService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDictServiceImpl
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户dict服务实现，位于 商户后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class MerchantDictServiceImpl implements MerchantDictService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 默认语言区域。
     */
    private static final String DEFAULT_LOCALE = "zh-CN";

    private final SysDictDataMapper dictDataMapper;
    private final DictionaryOptionCacheReader dictionaryOptionCacheReader;

    /**
     * 创建商户后台只读字典服务。
     *
     * @param dictDataMapper 字典数据 Mapper
     * @param dictionaryOptionCacheReader 跨系统启用字典下拉快照读取器
     */
    public MerchantDictServiceImpl(SysDictDataMapper dictDataMapper,
                                   DictionaryOptionCacheReader dictionaryOptionCacheReader) {
        this.dictDataMapper = dictDataMapper;
        this.dictionaryOptionCacheReader = dictionaryOptionCacheReader;
    }

    /**
     * 分页查询商户后台可用字典项。
     *
     * @param query 查询条件
     * @return 字典项分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<DictDataResponse> pageDictData(DictDataQuery query) {
        DictDataQuery safeQuery = query == null ? new DictDataQuery() : query;
        if (isEnabledOptionQuery(safeQuery)) {
            return pageCachedOptions(safeQuery);
        }
        IPage<SysDictDataDO> page = dictDataMapper.selectPage(
                new Page<>(safeQuery.safePageNo(), safeQuery.safePageSize()),
                buildQueryWrapper(safeQuery));
        return PageResult.of(
                page.getTotal(),
                safeQuery.safePageNo(),
                safeQuery.safePageSize(),
                page.getRecords().stream().map(this::toResponse).toList());
    }

    /** 判断请求是否为可使用有限期快照的纯下拉查询。 */
    private boolean isEnabledOptionQuery(DictDataQuery query) {
        return StringUtils.hasText(query.getDictType())
                && !StringUtils.hasText(query.getDictLabel())
                && !StringUtils.hasText(query.getDictValue())
                && !StringUtils.hasText(query.getParentValue())
                && (query.getStatus() == null || query.getStatus() == ENABLED);
    }

    /** 将指定字典的启用快照按请求页码切分为标准分页响应。 */
    private PageResult<DictDataResponse> pageCachedOptions(DictDataQuery query) {
        String locale = StringUtils.hasText(query.getLocale()) ? query.getLocale().trim() : DEFAULT_LOCALE;
        List<DictionaryOptionSnapshot> snapshots = dictionaryOptionCacheReader.findEnabled(
                query.getDictType().trim(),
                locale
        );
        long pageNo = query.safePageNo();
        long pageSize = query.safePageSize();
        long start = (pageNo - 1) * pageSize;
        int fromIndex = (int) Math.min(start, snapshots.size());
        int toIndex = (int) Math.min(start + pageSize, snapshots.size());
        List<DictDataResponse> records = snapshots.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResult.of(snapshots.size(), pageNo, pageSize, records);
    }

    /**
     * 构造商户字典查询条件。
     * <p>
     * 默认只查询未删除、启用、{@code zh-CN} 的字典项；调用方可指定 locale 和状态，
     * 但不能绕过逻辑删除边界。
     * </p>
     *
     * @param query 字典查询条件
     * @return 按排序值和主键升序的 MyBatis 查询包装器
     */
    private LambdaQueryWrapper<SysDictDataDO> buildQueryWrapper(DictDataQuery query) {
        String locale = StringUtils.hasText(query.getLocale()) ? query.getLocale() : DEFAULT_LOCALE;
        Integer status = query.getStatus() == null ? ENABLED : query.getStatus();
        return Wrappers.<SysDictDataDO>lambdaQuery()
                .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getDictType()), SysDictDataDO::getDictType, query.getDictType())
                .eq(StringUtils.hasText(query.getDictValue()), SysDictDataDO::getDictValue, query.getDictValue())
                .eq(StringUtils.hasText(query.getParentValue()), SysDictDataDO::getParentValue, query.getParentValue())
                .eq(SysDictDataDO::getLocale, locale)
                .eq(SysDictDataDO::getStatus, status)
                .likeRight(StringUtils.hasText(query.getDictLabel()), SysDictDataDO::getDictLabel, query.getDictLabel())
                .orderByAsc(SysDictDataDO::getDictSort)
                .orderByAsc(SysDictDataDO::getId);
    }

    /**
     * 将数据库字典记录转换为商户后台只读响应。
     *
     * @param entity 字典数据记录
     * @return 不包含数据库审计字段的字典响应
     */
    private DictDataResponse toResponse(SysDictDataDO entity) {
        DictDataResponse response = new DictDataResponse();
        response.setId(entity.getId());
        response.setDictType(entity.getDictType());
        response.setDictLabel(entity.getDictLabel());
        response.setDictValue(entity.getDictValue());
        response.setParentValue(entity.getParentValue());
        response.setLocale(entity.getLocale());
        response.setDictSort(entity.getDictSort());
        response.setListClass(entity.getListClass());
        response.setExtraJson(entity.getExtraJson());
        response.setIsDefault(entity.getIsDefault());
        response.setStatus(entity.getStatus());
        return response;
    }

    /** 将共享字典快照转换为商户端只读响应。 */
    private DictDataResponse toResponse(DictionaryOptionSnapshot snapshot) {
        DictDataResponse response = new DictDataResponse();
        response.setId(snapshot.getId());
        response.setDictType(snapshot.getDictType());
        response.setDictLabel(snapshot.getDictLabel());
        response.setDictValue(snapshot.getDictValue());
        response.setParentValue(snapshot.getParentValue());
        response.setLocale(snapshot.getLocale());
        response.setDictSort(snapshot.getDictSort());
        response.setListClass(snapshot.getListClass());
        response.setExtraJson(snapshot.getExtraJson());
        response.setIsDefault(snapshot.getIsDefault());
        response.setStatus(snapshot.getStatus());
        return response;
    }
}
