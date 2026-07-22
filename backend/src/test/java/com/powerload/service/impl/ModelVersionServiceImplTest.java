package com.powerload.service.impl;

import com.powerload.entity.ModelVersion;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.ml.FlaskInferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModelVersionServiceImplTest {

    private ModelVersionMapper mapper;
    private LoadDataMapper loadDataMapper;
    private FlaskInferenceService flaskInferenceService;
    private ModelVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ModelVersionMapper.class);
        loadDataMapper = mock(LoadDataMapper.class);
        flaskInferenceService = mock(FlaskInferenceService.class);
        when(flaskInferenceService.getHealth()).thenReturn(Map.of("healthy", true, "model_type", "torchscript"));
        service = new ModelVersionServiceImpl(mapper, loadDataMapper, flaskInferenceService);
        ReflectionTestUtils.setField(service, "modelDir", "not-existing-model-dir");
        ReflectionTestUtils.setField(service, "mlWorkDir", "../ml");
    }

    @Test
    void shouldListVersionsWithoutSyncingArtifacts() {
        when(mapper.selectList(any())).thenReturn(List.of(new ModelVersion()));

        assertEquals(1, service.listVersions().size());
        verify(mapper).selectList(any());
        verify(mapper, never()).delete(any());
        verify(flaskInferenceService, never()).getHealth();
    }

    @Test
    void shouldCleanInactiveMetriclessVersionsOnlyWhenSyncing() {
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of());

        service.syncLocalArtifacts();

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

    @Test
    void shouldPreferUnixPythonInDockerEvenIfWindowsVenvExists() throws Exception {
        Path workDir = Files.createTempDirectory("ml-workdir");
        Path windowsVenv = workDir.resolve(".venv").resolve("Scripts");
        Files.createDirectories(windowsVenv);
        Files.createFile(windowsVenv.resolve("python.exe"));

        String command = service.resolvePythonCommand(workDir, "Linux");

        assertEquals("python3", command);
    }

    @Test
    void shouldUseWindowsVenvOnWindows() throws Exception {
        Path workDir = Files.createTempDirectory("ml-workdir");
        Path windowsVenv = workDir.resolve(".venv").resolve("Scripts");
        Files.createDirectories(windowsVenv);
        Path pythonExe = Files.createFile(windowsVenv.resolve("python.exe"));

        String command = service.resolvePythonCommand(workDir, "Windows 11");

        assertEquals(pythonExe.toString(), command);
    }
}
