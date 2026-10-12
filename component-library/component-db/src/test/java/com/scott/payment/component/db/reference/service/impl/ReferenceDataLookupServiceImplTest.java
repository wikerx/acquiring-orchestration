package com.scott.payment.component.db.reference.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.entity.IpLibraryDataRow;
import com.scott.payment.component.db.reference.entity.IpLibraryShardDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.mapper.IpLocationLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpShardRoute;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReferenceDataLookupServiceImplTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 公共基础数据检索服务行为测试。
 * @status : create
 */
class ReferenceDataLookupServiceImplTest {

    @Test
    void shouldLookupIpv4LocationFromReadySlaveShard() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        IpShardRoute route = route("route-v1", "ip_library_v4_data_01", "20260811");
        IpLibraryDataRow row = new IpLibraryDataRow();
        LocalDateTime createTime = LocalDateTime.of(2026, 10, 12, 11, 30);
        row.setId(9L);
        row.setIpNumberStart("134744000");
        row.setIpNumberEnd("134744255");
        row.setCountryAlpha2("US");
        row.setCountryAlpha3("USA");
        row.setCountryNumeric("840");
        row.setCountryName("United States");
        row.setStateProvince("California");
        row.setCity("Mountain View");
        row.setDataVersion("20260811");
        row.setCreateTime(createTime);
        row.setCreateBy("system");
        when(routingSnapshot.resolve("IPV4", "134744072")).thenReturn(route);
        when(ipMapper.selectLookupCandidate("ip_library_v4_data_01", "20260811", "134744072"))
                .thenReturn(row);

        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                ipMapper, cardBinMapper, routingSnapshot);

        var snapshot = service.lookupIpSnapshot("008.008.008.008");
        IpLookupResult result = snapshot.result();

        assertThat(result.matched()).isTrue();
        assertThat(result.ipAddress()).isEqualTo("8.8.8.8");
        assertThat(result.ipType()).isEqualTo("IPV4");
        assertThat(result.countryAlpha3()).isEqualTo("USA");
        assertThat(result.city()).isEqualTo("Mountain View");
        assertThat(snapshot.detail().id()).isEqualTo(9L);
        assertThat(snapshot.detail().ipNumberStart()).isEqualTo("134744000");
        assertThat(snapshot.detail().ipNumberEnd()).isEqualTo("134744255");
        assertThat(snapshot.detail().dataVersion()).isEqualTo("20260811");
        assertThat(snapshot.detail().createTime()).isEqualTo(createTime);
        assertThat(snapshot.detail().createBy()).isEqualTo("system");
    }

    @Test
    void shouldNormalizeCardBinAndExecuteOneRangeQuery() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO row = new CardBinRangeDO();
        row.setId(9L);
        row.setCardBinStart(41111100000L);
        row.setCardBinEnd(41111199999L);
        row.setBinLength(6);
        row.setCardBrand("VISA");
        row.setCardSubBrand("CLASSIC");
        row.setCardType("CREDIT");
        row.setCardLevel("GOLD");
        row.setIssuerCountryName("United States");
        row.setIssuerCountryAlpha2("US");
        row.setIssuerCountryAlpha3("USA");
        row.setIssuerCountryNumeric("840");
        row.setIssuerBank("Example Bank");
        when(cardBinMapper.selectBestMatch(41111100000L)).thenReturn(row);

        ReferenceDataLookupServiceImpl service = service(ipMapper, cardBinMapper);

        CardBinLookupResult result = service.lookupCardBin("411111");

        assertThat(result.matched()).isTrue();
        assertThat(result.cardBin()).isEqualTo("41111100000");
        assertThat(result.binLength()).isEqualTo(6);
        assertThat(result.cardBrand()).isEqualTo("VISA");
        assertThat(result.issuerBank()).isEqualTo("Example Bank");
        verify(cardBinMapper).selectBestMatch(41111100000L);
    }

    @Test
    void shouldReturnStableMissResultsForValidInputs() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        IpShardRoute route = route("route-v1", "ip_library_v6_data_01", "20260811");
        when(routingSnapshot.resolve("IPV6", "1")).thenReturn(route);
        when(ipMapper.selectLookupCandidate("ip_library_v6_data_01", "20260811", "1")).thenReturn(null);
        when(cardBinMapper.selectBestMatch(99999900000L)).thenReturn(null);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                ipMapper, cardBinMapper, routingSnapshot);

        IpLookupResult ipResult = service.lookupIp("::1");
        CardBinLookupResult cardBinResult = service.lookupCardBin("999999");

        assertThat(ipResult.matched()).isFalse();
        assertThat(ipResult.ipAddress()).isEqualTo("0:0:0:0:0:0:0:1");
        assertThat(cardBinResult.matched()).isFalse();
        assertThat(cardBinResult.cardBin()).isEqualTo("99999900000");
    }

    @Test
    void shouldReturnMissAfterOneReplicaQuery() {
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        ReferenceDataLookupServiceImpl service = service(
                mock(IpLocationLookupMapper.class), cardBinMapper);

        CardBinLookupResult result = service.lookupCardBin("99999900000");

        assertThat(result.matched()).isFalse();
        verify(cardBinMapper).selectBestMatch(99999900000L);
    }

    @Test
    void shouldRejectInvalidIpAndCardBinBeforeDatabaseLookup() {
        ReferenceDataLookupServiceImpl service = service(
                mock(IpLocationLookupMapper.class), mock(CardBinLookupMapper.class));

        assertThatThrownBy(() -> service.lookupIp("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookupCardBin("41111A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookupCardBin("411111111111"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFailWhenIpShardConfigurationIsMissingOrAmbiguous() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        when(routingSnapshot.resolve("IPV4", "134744072"))
                .thenThrow(new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR));
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                ipMapper, cardBinMapper, routingSnapshot);

        assertThatThrownBy(() -> service.lookupIp("8.8.8.8"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRejectIpShardWhosePhysicalTableDoesNotMatchIpType() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        IpShardRoute mismatchedRoute = route("route-v1", "ip_library_v6_data_01", "20260811");
        when(routingSnapshot.resolve("IPV4", "134744072")).thenReturn(mismatchedRoute);
        ReferenceDataLookupServiceImpl service = new ReferenceDataLookupServiceImpl(
                ipMapper, cardBinMapper, routingSnapshot);

        assertThatThrownBy(() -> service.lookupIp("8.8.8.8"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRejectCardBinRowThatDoesNotCoverNormalizedValue() {
        IpLocationLookupMapper ipMapper = mock(IpLocationLookupMapper.class);
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO invalidRow = new CardBinRangeDO();
        invalidRow.setId(7L);
        invalidRow.setCardBinStart(41111200000L);
        invalidRow.setCardBinEnd(41111299999L);
        invalidRow.setBinLength(6);
        when(cardBinMapper.selectBestMatch(41111100000L)).thenReturn(invalidRow);
        ReferenceDataLookupServiceImpl service = service(ipMapper, cardBinMapper);

        assertThatThrownBy(() -> service.lookupCardBin("411111"))
                .isInstanceOfSatisfying(ServiceException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode()));
    }

    @Test
    void shouldRouteReferenceDataLookupsToSlaveDataSource() {
        assertThat(ReferenceDataLookupServiceImpl.class.getAnnotation(DS.class)).isNull();

        assertSlaveMethod("lookupIp", String.class);
        assertSlaveMethod("lookupIpSnapshot", String.class);
        assertSlaveMethod("lookupCardBin", String.class);
    }

    @Test
    void shouldReadCompleteElevenDigitResultFromSlave() {
        CardBinLookupMapper cardBinMapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO row = new CardBinRangeDO();
        row.setId(12L);
        row.setCardBinStart(41111112345L);
        row.setCardBinEnd(41111112345L);
        row.setBinLength(11);
        row.setCardBrand("VISA");
        row.setCardSubBrand("SIGNATURE");
        row.setIssuerCountryNumeric("840");
        row.setIssuerBank("Example Bank");
        when(cardBinMapper.selectBestMatch(41111112345L)).thenReturn(row);
        ReferenceDataLookupServiceImpl service = service(
                mock(IpLocationLookupMapper.class), cardBinMapper);

        CardBinLookupResult result = service.lookupCardBin("41111112345");

        assertThat(result.cardSubBrand()).isEqualTo("SIGNATURE");
        assertThat(result.issuerCountryNumeric()).isEqualTo("840");
        assertThat(result.issuerBank()).isEqualTo("Example Bank");
    }

    private void assertSlaveMethod(String methodName, Class<?>... parameterTypes) {
        Method method;
        try {
            method = ReferenceDataLookupServiceImpl.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.SLAVE);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transaction.readOnly()).isTrue();
    }

    private ReferenceDataLookupServiceImpl service(IpLocationLookupMapper ipMapper,
                                                   CardBinLookupMapper cardBinMapper) {
        return new ReferenceDataLookupServiceImpl(
                ipMapper, cardBinMapper, mock(IpShardRoutingSnapshot.class));
    }

    private IpShardRoute route(String routingVersion, String tableName, String dataVersion) {
        return new IpShardRoute(routingVersion, tableName, dataVersion);
    }
}
