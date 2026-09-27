package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.entity.merchant.MerchantIdSequenceDO;
import com.scott.payment.admin.mapper.MerchantIdSequenceMapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIdAllocationService
 * @date : 2026-09-17 18:10
 * @email : scott_x@163.com
 * @description : 商户号分配领域服务；按上海业务年份锁定数据库序列并生成 yy 加四位年度流水的六位商户号。
 * @status : create
 */
@Service
public class MerchantIdAllocationService {

    /** 单个业务年份最多分配 9999 个六位商户号；单位为个；非敏感配置；不允许为空。 */
    private static final int MAX_ANNUAL_SEQUENCE = 9_999;

    /** 商户号年份统一采用平台业务时区；格式为 IANA 时区；非敏感配置；不允许为空。 */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /** 商户号前两位年份格式；固定为 yy；非敏感配置；不允许为空。 */
    private static final DateTimeFormatter BUSINESS_YEAR_FORMATTER =
            DateTimeFormatter.ofPattern("yy", Locale.ROOT);

    /** 年度序列数据库访问组件；非敏感依赖；不允许为空。 */
    private final MerchantIdSequenceMapper sequenceMapper;

    /** 业务时间来源；测试可注入固定时钟；非敏感依赖；不允许为空。 */
    private final Clock clock;

    /**
     * 创建生产环境商户号分配服务。
     *
     * @param sequenceMapper 商户号年度序列 Mapper
     */
    @Autowired
    public MerchantIdAllocationService(MerchantIdSequenceMapper sequenceMapper) {
        this(sequenceMapper, Clock.system(BUSINESS_ZONE_ID));
    }

    MerchantIdAllocationService(MerchantIdSequenceMapper sequenceMapper, Clock clock) {
        this.sequenceMapper = sequenceMapper;
        this.clock = Objects.requireNonNull(clock, "merchant id clock is required");
    }

    /**
     * 在调用方的主库事务中分配六位商户号。
     *
     * <p>编号格式为两位年份加四位年度流水，例如 2026 年首个商户为 {@code 260001}。
     * 序列递增与商户主表插入共享事务，开户失败时不会永久消耗流水。</p>
     *
     * @return 六位纯数字商户号
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String allocate() {
        String businessYear = LocalDate.now(clock).format(BUSINESS_YEAR_FORMATTER);
        sequenceMapper.insertIfAbsent(businessYear);
        MerchantIdSequenceDO sequence = sequenceMapper.selectForUpdate(businessYear);
        if (sequence == null || sequence.getCurrentSequence() == null || sequence.getVersion() == null) {
            throw allocationFailure("商户号年度序列未正确初始化");
        }

        int currentSequence = sequence.getCurrentSequence();
        if (currentSequence < 0 || currentSequence >= MAX_ANNUAL_SEQUENCE) {
            throw allocationFailure("本年度六位商户号已用尽");
        }
        int nextSequence = currentSequence + 1;
        if (sequenceMapper.increment(businessYear, currentSequence, sequence.getVersion()) != 1) {
            throw allocationFailure("商户号年度序列并发更新失败");
        }
        return businessYear + String.format(Locale.ROOT, "%04d", nextSequence);
    }

    private ServiceException allocationFailure(String message) {
        return new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), message);
    }
}
