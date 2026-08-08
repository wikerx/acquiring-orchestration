package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRawRateDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeRateRuleDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateServiceImplTest
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : 管理端汇率领域服务单元测试，覆盖汇率精度、时间查询条件、排序和审计字段映射。
 * @status : create
 */
@Slf4j
class AdminExchangeRateServiceImplTest {

    /**
     * 初始化 LambdaQueryWrapper 解析字段名所需的 MyBatis Plus 表元数据。
     */
    @BeforeAll
    static void initializeTableMetadata() {
        log.info("初始化汇率实体 MyBatis Plus 表元数据");
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "exchange-rate-test");
        TableInfoHelper.initTableInfo(assistant, ExchangeRawRateDO.class);
        TableInfoHelper.initTableInfo(assistant, ExchangeBusinessRateDO.class);
    }

    /**
     * 验证按 BP 上浮时使用高精度十进制计算并按指定精度舍入。
     */
    @Test
    void shouldCalculateFinalRateWithBpMarkup() {
        log.info("开始验证 BP 上浮汇率计算，原始汇率=7.83250000，上浮=20BP，精度=8");
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("UP", "BP", "20", 8, "ROUND_HALF_UP");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("7.83250000"), rule);

        assertThat(finalRate).isEqualByComparingTo("7.84816500");
        log.info("BP 上浮汇率计算验证完成，结果={}", finalRate);
    }

    /**
     * 验证按百分比下调时遵循向下舍入规则。
     */
    @Test
    void shouldCalculateFinalRateWithPercentDiscountAndRoundDown() {
        log.info("开始验证百分比下调汇率计算，原始汇率=7.83256789，下调=0.30%，精度=8");
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("DOWN", "PERCENT", "0.30", 8, "ROUND_DOWN");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("7.83256789"), rule);

        assertThat(finalRate).isEqualByComparingTo("7.80907018");
        log.info("百分比下调汇率计算验证完成，结果={}", finalRate);
    }

    /**
     * 验证不调整方向仅执行最终精度舍入，不改变汇率业务值。
     */
    @Test
    void shouldKeepOriginalRateWhenAdjustDirectionIsNone() {
        log.info("开始验证无调整汇率计算，原始汇率=1.234567891，精度=8");
        AdminExchangeRateServiceImpl service = newService();
        ExchangeRateRuleDO rule = rule("NONE", "BP", "0", 8, "ROUND_HALF_UP");

        BigDecimal finalRate = service.calculateFinalRate(new BigDecimal("1.234567891"), rule);

        assertThat(finalRate).isEqualByComparingTo("1.23456789");
        log.info("无调整汇率计算验证完成，结果={}", finalRate);
    }

    /**
     * 验证原始汇率的三组时间边界均进入查询，并固定按拉取时间倒序。
     */
    @Test
    void shouldApplyRawRateTimeRangesAndSortByFetchTimeDesc() {
        log.info("开始验证原始汇率发布时间、拉取时间、生效时间筛选及默认排序");
        AdminExchangeRateServiceImpl service = newService();
        RawRateQuery query = new RawRateQuery();
        query.setPublishStartTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setPublishEndTime(LocalDateTime.of(2026, 8, 2, 0, 0));
        query.setFetchStartTime(LocalDateTime.of(2026, 8, 3, 0, 0));
        query.setFetchEndTime(LocalDateTime.of(2026, 8, 4, 0, 0));
        query.setEffectiveStartTime(LocalDateTime.of(2026, 8, 5, 0, 0));
        query.setEffectiveEndTime(LocalDateTime.of(2026, 8, 6, 0, 0));

        LambdaQueryWrapper<ExchangeRawRateDO> wrapper = service.buildRawRateQueryWrapper(query);
        String sql = normalizedSql(wrapper.getCustomSqlSegment());

        assertThat(sql).contains("publish_time", "fetch_time", "effective_time");
        assertThat(sql).containsPattern("order by\\s+fetch_time\\s+desc\\s*,\\s*id\\s+desc");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(
                query.getPublishStartTime(), query.getPublishEndTime(),
                query.getFetchStartTime(), query.getFetchEndTime(),
                query.getEffectiveStartTime(), query.getEffectiveEndTime());
        log.info("原始汇率时间筛选及排序验证完成，参数数量={}", wrapper.getParamNameValuePairs().size());
    }

    /**
     * 验证业务汇率生效时间和创建时间边界共同进入查询。
     */
    @Test
    void shouldApplyBusinessRateEffectiveAndCreateTimeRanges() {
        log.info("开始验证业务汇率生效时间和创建时间筛选");
        AdminExchangeRateServiceImpl service = newService();
        BusinessRateQuery query = new BusinessRateQuery();
        query.setEffectiveStartTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEffectiveEndTime(LocalDateTime.of(2026, 8, 2, 0, 0));
        query.setCreateStartTime(LocalDateTime.of(2026, 8, 3, 0, 0));
        query.setCreateEndTime(LocalDateTime.of(2026, 8, 4, 0, 0));

        LambdaQueryWrapper<ExchangeBusinessRateDO> wrapper = service.buildBusinessRateQueryWrapper(query);
        String sql = normalizedSql(wrapper.getCustomSqlSegment());

        assertThat(sql).contains("effective_time", "create_time");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(
                query.getEffectiveStartTime(), query.getEffectiveEndTime(),
                query.getCreateStartTime(), query.getCreateEndTime());
        log.info("业务汇率时间筛选验证完成，参数数量={}", wrapper.getParamNameValuePairs().size());
    }

    /**
     * 验证数据库审计人能够映射到管理端业务汇率响应。
     *
     * @throws ReflectiveOperationException 私有转换方法签名变化时由测试显式失败
     */
    @Test
    void shouldMapBusinessRateUpdateBy() throws ReflectiveOperationException {
        log.info("开始验证业务汇率更新人响应映射");
        AdminExchangeRateServiceImpl service = newService();
        ExchangeBusinessRateDO entity = new ExchangeBusinessRateDO();
        entity.setUpdateBy("admin-user");

        Method converter = AdminExchangeRateServiceImpl.class
                .getDeclaredMethod("toBusinessRateResponse", ExchangeBusinessRateDO.class);
        converter.setAccessible(true);
        BusinessRateResponse response = invokeBusinessRateConverter(converter, service, entity);

        assertThat(response.getUpdateBy()).isEqualTo("admin-user");
        log.info("业务汇率更新人响应映射验证完成，更新人={}", response.getUpdateBy());
    }

    private AdminExchangeRateServiceImpl newService() {
        return new AdminExchangeRateServiceImpl(null, null, null, null, null);
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private BusinessRateResponse invokeBusinessRateConverter(Method converter,
                                                              AdminExchangeRateServiceImpl service,
                                                              ExchangeBusinessRateDO entity)
            throws IllegalAccessException, InvocationTargetException {
        return (BusinessRateResponse) converter.invoke(service, entity);
    }

    private ExchangeRateRuleDO rule(String direction, String method, String value, int scale, String roundingMode) {
        ExchangeRateRuleDO rule = new ExchangeRateRuleDO();
        rule.setAdjustDirection(direction);
        rule.setAdjustMethod(method);
        rule.setAdjustValue(new BigDecimal(value));
        rule.setDecimalScale(scale);
        rule.setRoundingMode(roundingMode);
        return rule;
    }
}
