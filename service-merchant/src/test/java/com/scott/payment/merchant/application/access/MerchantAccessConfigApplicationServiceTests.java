package com.scott.payment.merchant.application.access;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessConfigApplicationServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证商户来源网址和 IP 白名单由 Merchant 本地持久化并始终绑定认证商户身份
 * @status : create
 */
class MerchantAccessConfigApplicationServiceTests {

    @Test
    void sourceUrlSubmissionShouldPersistPendingRecordInMerchantService() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class))).thenAnswer(invocation -> {
                    GeneratedKeyHolder keyHolder = invocation.getArgument(2);
                    keyHolder.getKeyList().add(Map.of("id", 101L));
                    return 1;
                });
        MerchantAccessConfigApplicationService service =
                new MerchantAccessConfigApplicationService(jdbcTemplate);
        SourceUrlSubmitRequest request = new SourceUrlSubmitRequest();
        request.setSourceUrls(List.of("https://Shop.Example.com/checkout"));
        request.setRemark("merchant submit");

        var created = service.submitSourceUrls(" M10000001 ", request);

        assertThat(created).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("101");
            assertThat(item.getMerchantId()).isEqualTo("M10000001");
            assertThat(item.getSourceHost()).isEqualTo("shop.example.com");
            assertThat(item.getStatus()).isZero();
            assertThat(item.getApprovalStatus()).isZero();
            assertThat(item.getSubmitSource()).isEqualTo("MERCHANT");
        });
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture(),
                any(KeyHolder.class), any(String[].class));
        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("merchantId")).isEqualTo("M10000001");
        assertThat(params.getValue("sourceHost")).isEqualTo("shop.example.com");
        assertThat(params.getValue("operator")).isEqualTo("MERCHANT:M10000001");
    }

    @Test
    void ipWhitelistSubmissionShouldNormalizeAndPersistPendingRecordLocally() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class))).thenReturn(1);
        MerchantAccessConfigApplicationService service =
                new MerchantAccessConfigApplicationService(jdbcTemplate);
        IpWhitelistSubmitRequest request = new IpWhitelistSubmitRequest();
        request.setIpValues(List.of("192.168.001.010"));

        var created = service.submitIpWhitelists("M10000001", request);

        assertThat(created).singleElement().satisfies(item -> {
            assertThat(item.getMerchantId()).isEqualTo("M10000001");
            assertThat(item.getIpType()).isEqualTo("IPv4");
            assertThat(item.getIpValue()).isEqualTo("192.168.1.10");
            assertThat(item.getStatus()).isZero();
            assertThat(item.getApprovalStatus()).isZero();
            assertThat(item.getSubmitSource()).isEqualTo("MERCHANT");
        });
    }

    @Test
    void sourceUrlSubmissionShouldRejectNonHttpUrlBeforeDatabaseWrite() {
        MerchantAccessConfigApplicationService service =
                new MerchantAccessConfigApplicationService(mock(NamedParameterJdbcTemplate.class));
        SourceUrlSubmitRequest request = new SourceUrlSubmitRequest();
        request.setSourceUrls(List.of("javascript:alert(1)"));

        assertThatThrownBy(() -> service.submitSourceUrls("M10000001", request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void queryMethodsShouldUseLocalMasterDataSource() throws NoSuchMethodException {
        assertThat(MerchantAccessConfigApplicationService.class
                .getMethod("listSourceUrls", String.class)
                .getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
        assertThat(MerchantAccessConfigApplicationService.class
                .getMethod("listIpWhitelists", String.class)
                .getAnnotation(DS.class).value()).isEqualTo(DataSourceName.MASTER);
        assertThat(MerchantAccessConfigApplicationService.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(NamedParameterJdbcTemplate.class));
    }
}
