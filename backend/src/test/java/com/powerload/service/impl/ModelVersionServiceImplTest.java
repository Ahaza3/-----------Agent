package com.powerload.service.impl;

import com.powerload.entity.ModelVersion;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.ml.FlaskInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModelVersionServiceImplTest {

    private ModelVersionMapper mapper;
    private FlaskInferenceService flaskInferenceService;
    private ModelVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ModelVersionMapper.class);
        flaskInferenceService = mock(FlaskInferenceService.class);
        when(flaskInferenceService.getHealth()).thenReturn(Map.of("healthy", true, "model_type", "torchscript"));
        service = new ModelVersionServiceImpl(mapper, flaskInferenceService);
        ReflectionTestUtils.setField(service, "modelDir", "not-existing-model-dir");
        ReflectionTestUtils.setField(service, "mlWorkDir", "../ml");
    }

    @Test
    void shouldListVersionsWithActiveFirst() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectList(any())).thenReturn(List.of(new ModelVersion()));

        assertEquals(1, service.listVersions().size());
        verify(mapper).delete(any());
        verify(mapper).selectList(any());
    }

    @Test
    void shouldCleanInactiveMetriclessVersionsWhenListing() {
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of());

        service.listVersions();

        verify(mapper).delete(any());
    }

    @Test
    void shouldActivateOnlySelectedVersion() {
        ModelVersion target = new ModelVersion();
        target.setId(2L);
        target.setIsActive(0);
        when(mapper.selectById(2L)).thenReturn(target);
        when(mapper.selectById(2L)).thenReturn(target);

        ModelVersion result = service.activate(2L);

        assertSame(target, result);
        assertEquals(1, target.getIsActive());
        verify(mapper).update(isNull(), any());
        verify(mapper).updateById(any(ModelVersion.class));
    }

    @Test
    void shouldRejectUnknownVersion() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.activate(99L));
        verify(mapper, never()).updateById(any(ModelVersion.class));
    }
}
