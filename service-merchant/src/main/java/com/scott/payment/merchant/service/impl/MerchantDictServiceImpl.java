package com.scott.payment.merchant.service.impl;

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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDictServiceImpl
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台只读字典服务实现，位于 service-merchant 服务实现层，仅读取启用字典项供页面筛选和展示。
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

    /**
     * dict Data Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysDictDataMapper dictDataMapper;

    /**
     * 创建商户后台只读字典服务。
     *
     * @param dictDataMapper 字典数据 Mapper
     */
    public MerchantDictServiceImpl(SysDictDataMapper dictDataMapper) {
        this.dictDataMapper = dictDataMapper;
    }

    /**
     * 分页查询商户后台可用字典项。
     *
     * @param query 查询条件
     * @return 字典项分页结果
     */
    @Override
    public PageResult<DictDataResponse> pageDictData(DictDataQuery query) {
        DictDataQuery safeQuery = query == null ? new DictDataQuery() : query;
        IPage<SysDictDataDO> page = dictDataMapper.selectPage(
                new Page<>(safeQuery.safePageNo(), safeQuery.safePageSize()),
                buildQueryWrapper(safeQuery));
        return PageResult.of(
                page.getTotal(),
                safeQuery.safePageNo(),
                safeQuery.safePageSize(),
                page.getRecords().stream().map(this::toResponse).toList());
    }

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
}
