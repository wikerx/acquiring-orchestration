package com.scott.payment.admin.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.converter.DeptConverter;
import com.scott.payment.admin.converter.IsoCountryConverter;
import com.scott.payment.admin.converter.IsoCurrencyConverter;
import com.scott.payment.admin.converter.NoticeConverter;
import com.scott.payment.admin.converter.PostConverter;
import com.scott.payment.admin.dto.base.IsoCountryDTOs;
import com.scott.payment.admin.dto.base.IsoCurrencyDTOs;
import com.scott.payment.admin.dto.system.DeptDTOs;
import com.scott.payment.admin.dto.system.NoticeDTOs;
import com.scott.payment.admin.dto.system.PostDTOs;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.entity.SysNoticeDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端持久化边界 DTO 的兼容性测试。
 *
 * <p>验证写请求不会绑定持久化控制字段，同时响应仍保留原接口已经公开的字段。</p>
 */
class AdminPersistenceBoundaryDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 写请求应忽略主键、删除标记和未来扩展字段。
     *
     * @throws Exception JSON 反序列化失败时抛出
     */
    @Test
    void saveRequestsShouldIgnorePersistenceAndUnknownFields() throws Exception {
        IsoCountryDTOs.SaveRequest country = objectMapper.readValue(
                "{\"alpha2Code\":\"US\",\"id\":9,\"deleted\":1,\"futureField\":true}",
                IsoCountryDTOs.SaveRequest.class);
        IsoCurrencyDTOs.SaveRequest currency = objectMapper.readValue(
                "{\"alpha3Code\":\"USD\",\"id\":9,\"deleted\":1,\"futureField\":true}",
                IsoCurrencyDTOs.SaveRequest.class);
        NoticeDTOs.SaveRequest notice = objectMapper.readValue(
                "{\"noticeTitle\":\"Maintenance\",\"createBy\":\"forged\",\"futureField\":true}",
                NoticeDTOs.SaveRequest.class);
        PostDTOs.SaveRequest post = objectMapper.readValue(
                "{\"postCode\":\"OPS\",\"appId\":99,\"deleted\":1,\"futureField\":true}",
                PostDTOs.SaveRequest.class);
        DeptDTOs.SaveRequest dept = objectMapper.readValue(
                "{\"deptName\":\"Operations\",\"appId\":99,\"deleted\":1,\"futureField\":true}",
                DeptDTOs.SaveRequest.class);

        assertThat(country.getAlpha2Code()).isEqualTo("US");
        assertThat(currency.getAlpha3Code()).isEqualTo("USD");
        assertThat(notice.getNoticeTitle()).isEqualTo("Maintenance");
        assertThat(post.getPostCode()).isEqualTo("OPS");
        assertThat(dept.getDeptName()).isEqualTo("Operations");
    }

    /**
     * 响应转换应保留既有管理端接口已经公开的业务和审计字段。
     */
    @Test
    void convertersShouldPreserveExistingResponseFields() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 4, 0);

        IsoCountryDO country = new IsoCountryDO();
        country.setId(1L);
        country.setAlpha2Code("US");
        country.setCreatedAt(now);
        country.setDeleted(0);

        IsoCurrencyDO currency = new IsoCurrencyDO();
        currency.setId(2L);
        currency.setAlpha3Code("USD");
        currency.setMinimumAmount(new BigDecimal("0.01"));
        currency.setDeleted(0);

        SysNoticeDO notice = new SysNoticeDO();
        notice.setId(3L);
        notice.setNoticeTitle("Maintenance");
        notice.setCreateBy("admin");
        notice.setDeleted(0L);

        SysPostDO post = new SysPostDO();
        post.setId(4L);
        post.setAppId(11L);
        post.setPostCode("OPS");
        post.setDeleted(0L);

        SysDeptDO dept = new SysDeptDO();
        dept.setId(5L);
        dept.setAppId(11L);
        dept.setDeptName("Operations");
        dept.setDeleted(0L);

        IsoCountryDTOs.Response countryResponse = Mappers.getMapper(IsoCountryConverter.class)
                .toResponse(country);
        IsoCurrencyDTOs.Response currencyResponse = Mappers.getMapper(IsoCurrencyConverter.class)
                .toResponse(currency);
        NoticeDTOs.Response noticeResponse = Mappers.getMapper(NoticeConverter.class).toResponse(notice);
        PostDTOs.Response postResponse = Mappers.getMapper(PostConverter.class).toResponse(post);
        DeptDTOs.Response deptResponse = Mappers.getMapper(DeptConverter.class).toResponse(dept);

        assertThat(countryResponse)
                .extracting(IsoCountryDTOs.Response::getId, IsoCountryDTOs.Response::getAlpha2Code,
                        IsoCountryDTOs.Response::getCreatedAt, IsoCountryDTOs.Response::getDeleted)
                .containsExactly(1L, "US", now, 0);
        assertThat(currencyResponse)
                .extracting(IsoCurrencyDTOs.Response::getId, IsoCurrencyDTOs.Response::getAlpha3Code,
                        IsoCurrencyDTOs.Response::getMinimumAmount, IsoCurrencyDTOs.Response::getDeleted)
                .containsExactly(2L, "USD", new BigDecimal("0.01"), 0);
        assertThat(noticeResponse)
                .extracting(NoticeDTOs.Response::getId, NoticeDTOs.Response::getCreateBy,
                        NoticeDTOs.Response::getDeleted)
                .containsExactly(3L, "admin", 0L);
        assertThat(postResponse)
                .extracting(PostDTOs.Response::getId, PostDTOs.Response::getAppId,
                        PostDTOs.Response::getDeleted)
                .containsExactly(4L, 11L, 0L);
        assertThat(deptResponse)
                .extracting(DeptDTOs.Response::getId, DeptDTOs.Response::getAppId,
                        DeptDTOs.Response::getDeleted)
                .containsExactly(5L, 11L, 0L);
    }
}
