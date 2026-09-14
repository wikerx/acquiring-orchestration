package com.scott.payment.admin.service.monitor;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorTimeRangeNormalizerTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 监控查询时区转换、时间桶选择和最大七天范围校验测试。
 * @status : create
 */
class MonitorTimeRangeNormalizerTest {

    private final MonitorTimeRangeNormalizer normalizer = new MonitorTimeRangeNormalizer();

    @Test
    void shouldConvertQueryRangeToDatabaseTimezoneAndSelectBucket() {
        MonitorTimeRangeNormalizer.NormalizedRange range = normalizer.normalize(
                LocalDateTime.of(2026, 9, 12, 0, 0),
                LocalDateTime.of(2026, 9, 12, 6, 0),
                "UTC");

        assertThat(range.beginTime()).isEqualTo(LocalDateTime.of(2026, 9, 12, 8, 0));
        assertThat(range.endTime()).isEqualTo(LocalDateTime.of(2026, 9, 12, 14, 0));
        assertThat(range.bucketMinutes()).isEqualTo(15);
    }

    @Test
    void shouldRejectRangesLongerThanSevenDays() {
        LocalDateTime end = LocalDateTime.of(2026, 9, 13, 12, 0);

        assertThatThrownBy(() -> normalizer.normalize(end.minusDays(7).minusSeconds(1), end, "Asia/Shanghai"))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.PARAM_INVALID.getCode()));
    }

    @Test
    void shouldRejectInvalidTimezone() {
        assertThatThrownBy(() -> normalizer.normalize(null, null, "Mars/Olympus"))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiResultEnum.PARAM_INVALID.getCode()));
    }
}
