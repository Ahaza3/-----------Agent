package com.powerload.realtime;

import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.entity.RealtimeTelemetry;
import com.powerload.mapper.RealtimeTelemetryMapper;
import com.powerload.service.impl.RealtimeLoadServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeTelemetryPersistenceTest {

    @Test
    void goodEstimatedLateAndBadPointsFollowTheQualityContract() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        RealtimeLoadServiceImpl service = serviceWith(mapper);
        LocalDateTime now = LocalDateTime.now();

        RealtimeLoadPoint good = point(1, 900f, now, now);
        RealtimeLoadPoint estimated = point(2, 901f, now.plusSeconds(1), now.plusSeconds(1));
        estimated.setEstimated(true);
        RealtimeLoadPoint late = point(3, 902f, now.minusSeconds(20), now);
        RealtimeLoadPoint bad = point(4, -1f, now.plusSeconds(2), now.plusSeconds(2));

        assertEquals("GOOD", service.accept(good).getQualityCode());
        assertEquals("ESTIMATED", service.accept(estimated).getQualityCode());
        assertEquals("LATE", service.accept(late).getQualityCode());
        assertEquals("BAD", service.accept(bad).getQualityCode());
        assertEquals(3, service.size());
        assertEquals(900f, service.getLatestForAlert().getLoadMw());
        verify(mapper, times(4)).insert(any(RealtimeTelemetry.class));
    }

    @Test
    void duplicateDoesNotPersistOrEnterMemoryTwiceAndDifferentInstanceIsAccepted() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        RealtimeLoadServiceImpl service = serviceWith(mapper);
        LocalDateTime now = LocalDateTime.now();

        assertNotNull(service.accept(point(7, 900f, now, now)));
        assertNull(service.accept(point(7, 900f, now, now)));
        RealtimeLoadPoint otherInstance = point(7, 901f, now.plusSeconds(1), now.plusSeconds(1));
        otherInstance.setSourceInstanceId("other-instance");
        assertNotNull(service.accept(otherInstance));

        assertEquals(2, service.size());
        verify(mapper, times(2)).insert(any(RealtimeTelemetry.class));
    }

    @Test
    void databaseDuplicateIsDroppedBeforeTheMemoryCurve() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(RealtimeTelemetry.class));
        RealtimeLoadServiceImpl service = serviceWith(mapper);

        assertNull(service.accept(point(1, 900f, LocalDateTime.now(), LocalDateTime.now())));
        assertEquals(0, service.size());
    }

    @Test
    void latePointIsPersistedButCannotReplaceLatestObservedPoint() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        RealtimeLoadServiceImpl service = serviceWith(mapper);
        LocalDateTime now = LocalDateTime.now();
        RealtimeLoadPoint current = point(2, 900f, now, now);
        RealtimeLoadPoint late = point(1, 800f, now.minusSeconds(30), now);

        service.accept(current);
        service.accept(late);

        assertEquals(2, service.getLatest().getSequence());
        assertEquals("LATE", service.getRecent(1).get(0).getQualityCode());
    }

    @Test
    void restartRecoveryLoadsRecentNonBadPointsWithoutCreatingAlertEligibleInput() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        RealtimeTelemetry good = telemetry(1, "GOOD", LocalDateTime.now().minusSeconds(2));
        RealtimeTelemetry bad = telemetry(2, "BAD", LocalDateTime.now().minusSeconds(1));
        when(mapper.selectList(any())).thenReturn(List.of(good, bad));
        RealtimeLoadServiceImpl service = serviceWith(mapper);

        service.recoverRecentTelemetry();

        assertEquals(1, service.size());
        assertNull(service.getLatestForAlert());
        verify(mapper, never()).insert(any(RealtimeTelemetry.class));
    }

    @Test
    void persistenceFailureKeepsTheInMemoryDegradedPath() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        doThrow(new IllegalStateException("offline")).when(mapper).insert(any(RealtimeTelemetry.class));
        RealtimeLoadServiceImpl service = serviceWith(mapper);

        RealtimeLoadPoint accepted = service.accept(point(1, 900f, LocalDateTime.now(), LocalDateTime.now()));

        assertNotNull(accepted);
        assertEquals("PERSISTENCE_DEGRADED", accepted.getPersistenceStatus());
        assertEquals(1, service.size());
    }

    @Test
    void nullNanInfinityAndNegativeLoadsAreAuditedAsBadOnly() {
        RealtimeTelemetryMapper mapper = mock(RealtimeTelemetryMapper.class);
        RealtimeLoadServiceImpl service = serviceWith(mapper);
        LocalDateTime now = LocalDateTime.now();

        assertEquals("BAD", service.accept(point(1, null, now, now)).getQualityCode());
        assertEquals("BAD", service.accept(point(2, Float.NaN, now, now)).getQualityCode());
        assertEquals("BAD", service.accept(point(3, Float.POSITIVE_INFINITY, now, now)).getQualityCode());
        assertEquals("BAD", service.accept(point(4, -1f, now, now)).getQualityCode());

        assertEquals(0, service.size());
        verify(mapper, times(4)).insert(any(RealtimeTelemetry.class));
    }

    private RealtimeLoadServiceImpl serviceWith(RealtimeTelemetryMapper mapper) {
        RealtimeLoadServiceImpl service = new RealtimeLoadServiceImpl();
        ReflectionTestUtils.setField(service, "realtimeTelemetryMapper", mapper);
        return service;
    }

    private RealtimeLoadPoint point(long sequence, Float load, LocalDateTime observedAt, LocalDateTime receivedAt) {
        RealtimeLoadPoint point = new RealtimeLoadPoint();
        point.setSequence(sequence);
        point.setLoadMw(load);
        point.setObservedAt(observedAt);
        point.setReceivedAt(receivedAt);
        point.setDataSource("MOCK_REALTIME");
        point.setSourceInstanceId("source-instance");
        point.setSource("MOCK");
        return point;
    }

    private RealtimeTelemetry telemetry(long sequence, String qualityCode, LocalDateTime observedAt) {
        RealtimeTelemetry telemetry = new RealtimeTelemetry();
        telemetry.setNodeId(1L);
        telemetry.setSequence(sequence);
        telemetry.setLoadMw(java.math.BigDecimal.valueOf(900));
        telemetry.setObservedAt(observedAt);
        telemetry.setReceivedAt(observedAt);
        telemetry.setSourceInstanceId("source-instance");
        telemetry.setDataSource("MOCK_REALTIME");
        telemetry.setQualityCode(qualityCode);
        telemetry.setPersistenceStatus("PERSISTED");
        telemetry.setEstimated(false);
        return telemetry;
    }
}
