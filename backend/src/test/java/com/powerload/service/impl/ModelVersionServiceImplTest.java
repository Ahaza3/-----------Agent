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
    void shouldNotDeleteLegacyVersionsWhenSyncing() {
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of());

        service.syncLocalArtifacts();

        verify(mapper, never()).delete(any());
    }

    @Test
    void shouldTreatMatchingRuntimeAsIdempotentActivation() throws Exception {
        ModelVersion target = new ModelVersion();
        target.setId(2L);
        target.setVersion("train-20260723");
        target.setArtifactDir("train-20260723");
        target.setArtifactChecksum("a".repeat(64));
        ReflectionTestUtils.setField(service, "modelDir", Files.createTempDirectory("models").toString());
        Path directory = Path.of((String) ReflectionTestUtils.getField(service, "modelDir")).resolve("train-20260723");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("model.pt"), "model");
        String fileChecksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("model".getBytes()));
        String checksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(("model.pt\n" + fileChecksum + "\n").getBytes()));
        target.setArtifactChecksum(checksum);
        Files.writeString(directory.resolve("manifest.json"), "{\"modelVersion\":\"train-20260723\",\"modelType\":\"LSTM\",\"artifactChecksum\":\"" + checksum + "\",\"files\":[{\"path\":\"model.pt\",\"sha256\":\"" + fileChecksum + "\"}]}");
        when(mapper.selectById(2L)).thenReturn(target);
        when(mapper.selectOne(any())).thenReturn(target);
        when(flaskInferenceService.getHealth()).thenReturn(Map.of("healthy", true, "modelVersion", target.getVersion(), "artifactChecksum", checksum));

        Map<String, Object> result = service.activate(2L, "request-1");

        assertEquals("CONSISTENT", result.get("consistency"));
        verify(mapper, never()).updateById(any(ModelVersion.class));
    }

    @Test
    void shouldRejectUnknownVersion() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.activate(99L, "request-1"));
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
