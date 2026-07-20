package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.powerload.entity.LoadData;
import com.powerload.entity.ModelVersion;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.ml.FlaskInferenceService;
import com.powerload.service.ModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModelVersionServiceImpl implements ModelVersionService {

    private final ModelVersionMapper modelVersionMapper;
    private final LoadDataMapper loadDataMapper;
    private final FlaskInferenceService flaskInferenceService;

    private static final int MIN_TRAINING_ROWS = 192;
    @Value("${ml.model-dir:../ml/models}")
    private String modelDir;

    @Value("${ml.work-dir:../ml}")
    private String mlWorkDir;

    private static final DateTimeFormatter VERSION_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern MAPE_PATTERN = Pattern.compile("MAPE\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern RMSE_PATTERN = Pattern.compile("RMSE\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private final AtomicReference<RetrainJob> retrainJob = new AtomicReference<>(RetrainJob.idle());

    @Override
    public List<ModelVersion> listVersions() {
        return syncLocalArtifacts();
    }

    @Override
    @Transactional
    public ModelVersion activate(Long id) {
        ModelVersion target = modelVersionMapper.selectById(id);
        if (target == null) {
            throw new IllegalArgumentException("模型版本不存在: " + id);
        }

        modelVersionMapper.update(null,
                new UpdateWrapper<ModelVersion>()
                        .set("is_active", 0)
                        .eq("is_active", 1));

        target.setIsActive(1);
        modelVersionMapper.updateById(target);
        return modelVersionMapper.selectById(id);
    }

    @Override
    @Transactional
    public List<ModelVersion> syncLocalArtifacts() {
        Path models = resolveModelDir();
        boolean hasActive = modelVersionMapper.selectCount(
                new LambdaQueryWrapper<ModelVersion>().eq(ModelVersion::getIsActive, 1)) > 0;
        String runtimeModel = String.valueOf(flaskInferenceService.getHealth().getOrDefault("model_type", ""));

        hasActive = registerArtifact(models.resolve("lstm_scripted.pt"), "LSTM", runtimeModel, hasActive);
        registerArtifact(models.resolve("prophet_model.pkl"), "Prophet", runtimeModel, hasActive);
        removeMetriclessInactiveVersions();
        return listStoredVersions();
    }

    @Override
    public Map<String, Object> startRetrain(String modelName) {
        String normalized = normalizeModelName(modelName);
        RetrainJob current = retrainJob.get();
        if ("RUNNING".equals(current.status())) {
            throw new IllegalStateException("已有模型训练任务正在执行，请等待完成后再重试");
        }

        RetrainJob next = new RetrainJob("RUNNING", normalized, LocalDateTime.now(), null, "训练任务已启动", "");
        retrainJob.set(next);
        Thread worker = new Thread(() -> runRetrain(normalized), "model-retrain-" + normalized.toLowerCase(Locale.ROOT));
        worker.setDaemon(true);
        worker.start();
        return next.toMap();
    }

    @Override
    public Map<String, Object> retrainStatus() {
        return retrainJob.get().toMap();
    }

    private List<ModelVersion> listStoredVersions() {
        return modelVersionMapper.selectList(
                new LambdaQueryWrapper<ModelVersion>()
                        .orderByDesc(ModelVersion::getIsActive)
                        .orderByDesc(ModelVersion::getTrainedAt)
                        .orderByDesc(ModelVersion::getCreatedAt));
    }

    private void removeMetriclessInactiveVersions() {
        modelVersionMapper.delete(
                new LambdaQueryWrapper<ModelVersion>()
                        .eq(ModelVersion::getIsActive, 0)
                        .and(wrapper -> wrapper
                                .isNull(ModelVersion::getMape)
                                .or()
                                .isNull(ModelVersion::getRmse)));
    }

    private boolean registerArtifact(Path artifact, String modelName, String runtimeModel, boolean hasActive) {
        if (!Files.exists(artifact)) {
            return hasActive;
        }
        LocalDateTime trainedAt = lastModified(artifact);
        List<ModelVersion> sameArtifactVersions = modelVersionMapper.selectList(
                new LambdaQueryWrapper<ModelVersion>()
                        .eq(ModelVersion::getModelName, modelName)
                        .eq(ModelVersion::getFilePath, artifact.toString()));
        LocalDateTime latestManualRetrain = sameArtifactVersions.stream()
                .filter(versionRow -> versionRow.getVersion() != null && versionRow.getVersion().startsWith("train-"))
                .map(ModelVersion::getTrainedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (latestManualRetrain != null && !latestManualRetrain.isBefore(trainedAt)) {
            sameArtifactVersions.stream()
                    .filter(versionRow -> versionRow.getVersion() != null && versionRow.getVersion().startsWith("art-"))
                    .forEach(versionRow -> modelVersionMapper.deleteById(versionRow.getId()));
            return hasActive;
        }
        String version = "art-" + VERSION_TIME.format(trainedAt);
        Long existing = modelVersionMapper.selectCount(new LambdaQueryWrapper<ModelVersion>()
                .eq(ModelVersion::getModelName, modelName)
                .eq(ModelVersion::getVersion, version));
        if (existing != null && existing > 0) {
            return hasActive;
        }

        boolean shouldActivate = !hasActive && matchesRuntime(modelName, runtimeModel);
        if (!hasActive && !shouldActivate && modelVersionMapper.selectCount(null) == 0) {
            shouldActivate = true;
        }

        ModelVersion versionRow = new ModelVersion();
        versionRow.setModelName(modelName);
        versionRow.setVersion(version);
        versionRow.setMape(null);
        versionRow.setRmse(null);
        versionRow.setFilePath(artifact.toString());
        versionRow.setHyperparams("{\"source\":\"LOCAL_ARTIFACT\"}");
        versionRow.setIsActive(shouldActivate ? 1 : 0);
        versionRow.setTrainedAt(trainedAt);
        versionRow.setCreatedAt(LocalDateTime.now());
        modelVersionMapper.insert(versionRow);
        return hasActive || shouldActivate;
    }

    private void runRetrain(String modelName) {
        String script = "PROPHET".equals(modelName) ? "train_prophet.py" : "train_lstm.py";
        Path workDir = resolveWorkDir();
        Path python = workDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        String pythonCommand = Files.exists(python) ? python.toString() : "python";
        StringBuilder output = new StringBuilder();
        try {
            prepareTrainingData(workDir, pythonCommand, output);
            ProcessBuilder pb = new ProcessBuilder(pythonCommand, script, "--input", "featured_load_data.csv");
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                retrainJob.set(retrainJob.get().finish("FAILED", "训练脚本退出码: " + exitCode, tail(output)));
                return;
            }
            insertRetrainedVersion(modelName, output.toString());
            retrainJob.set(retrainJob.get().finish("SUCCESS", "训练完成，新版本已登记", tail(output)));
        } catch (Exception e) {
            retrainJob.set(retrainJob.get().finish("FAILED", e.getMessage(), tail(output)));
        }
    }

    private void prepareTrainingData(Path workDir, String pythonCommand, StringBuilder output)
            throws IOException, InterruptedException {
        List<LoadData> rows = loadDataMapper.selectList(
                new LambdaQueryWrapper<LoadData>()
                        .apply("MINUTE(time) = 0 AND SECOND(time) = 0")
                        .isNotNull(LoadData::getTime)
                        .isNotNull(LoadData::getLoadMw)
                        .orderByAsc(LoadData::getTime));
        if (rows.size() < MIN_TRAINING_ROWS) {
            throw new IllegalStateException("可用于重训练的整点负荷数据不足，至少需要 "
                    + MIN_TRAINING_ROWS + " 条，当前只有 " + rows.size() + " 条");
        }

        Path rawData = workDir.resolve("retrain_load_data.csv");
        writeTrainingData(rawData, rows);
        output.append("训练数据已从 load_data 导出: ")
                .append(rows.size())
                .append(" 条 -> ")
                .append(rawData)
                .append(System.lineSeparator());

        ProcessBuilder featureProcess = new ProcessBuilder(
                pythonCommand,
                "feature_engineering.py",
                "--input", rawData.getFileName().toString(),
                "--output", "featured_load_data.csv");
        featureProcess.directory(workDir.toFile());
        featureProcess.redirectErrorStream(true);
        featureProcess.environment().put("PYTHONIOENCODING", "utf-8");
        featureProcess.environment().put("PYTHONUTF8", "1");
        Process process = featureProcess.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("特征工程脚本退出码: " + exitCode);
        }
    }

    private void writeTrainingData(Path outputPath, List<LoadData> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath, StandardCharsets.UTF_8)) {
            writer.write("time,load_mw,temperature,humidity,is_holiday,hour,day_of_week,month");
            writer.newLine();
            for (LoadData row : rows) {
                LocalDateTime time = row.getTime();
                writer.write(String.join(",",
                        time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        String.valueOf(row.getLoadMw()),
                        String.valueOf(row.getTemperature() == null ? 0f : row.getTemperature()),
                        String.valueOf(row.getHumidity() == null ? 0f : row.getHumidity()),
                        String.valueOf(row.getIsHoliday() == null ? 0 : row.getIsHoliday()),
                        String.valueOf(row.getHour() == null ? time.getHour() : row.getHour()),
                        String.valueOf(row.getDayOfWeek() == null ? time.getDayOfWeek().getValue() - 1 : row.getDayOfWeek()),
                        String.valueOf(row.getMonth() == null ? time.getMonthValue() : row.getMonth())));
                writer.newLine();
            }
        }
    }

    private void insertRetrainedVersion(String normalizedModelName, String output) {
        String displayName = "PROPHET".equals(normalizedModelName) ? "Prophet" : "LSTM";
        Path artifact = resolveModelDir()
                .resolve("PROPHET".equals(normalizedModelName) ? "prophet_model.pkl" : "lstm_scripted.pt");
        LocalDateTime now = LocalDateTime.now();
        boolean hasActive = modelVersionMapper.selectCount(
                new LambdaQueryWrapper<ModelVersion>().eq(ModelVersion::getIsActive, 1)) > 0;

        ModelVersion version = new ModelVersion();
        version.setModelName(displayName);
        version.setVersion("train-" + VERSION_TIME.format(now));
        version.setMape(extractMetric(output, MAPE_PATTERN));
        version.setRmse(extractMetric(output, RMSE_PATTERN));
        version.setFilePath(artifact.toString());
        version.setHyperparams("{\"source\":\"MANUAL_RETRAIN\",\"script\":\""
                + ("PROPHET".equals(normalizedModelName) ? "train_prophet.py" : "train_lstm.py") + "\"}");
        version.setIsActive(hasActive ? 0 : 1);
        version.setTrainedAt(now);
        version.setCreatedAt(now);
        modelVersionMapper.insert(version);
    }

    private String normalizeModelName(String modelName) {
        String normalized = modelName == null ? "LSTM" : modelName.trim().toUpperCase(Locale.ROOT);
        if ("PROPHET".equals(normalized) || "LSTM".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("modelName 仅支持 LSTM 或 PROPHET");
    }

    private boolean matchesRuntime(String modelName, String runtimeModel) {
        if (runtimeModel == null) {
            return false;
        }
        String runtime = runtimeModel.toLowerCase(Locale.ROOT);
        return ("LSTM".equals(modelName) && "torchscript".equals(runtime))
                || ("Prophet".equals(modelName) && "prophet".equals(runtime));
    }

    private LocalDateTime lastModified(Path path) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(path.toFile().lastModified()), ZoneId.systemDefault());
    }

    private Path resolveModelDir() {
        Path configured = Paths.get(modelDir);
        if (configured.isAbsolute() || Files.exists(configured) || !isDefaultPath(modelDir, "../ml/models", "ml/models")) {
            return configured.toAbsolutePath().normalize();
        }
        Path projectRootPath = Paths.get("ml/models");
        if (Files.exists(projectRootPath)) {
            return projectRootPath.toAbsolutePath().normalize();
        }
        return Paths.get("../ml/models").toAbsolutePath().normalize();
    }

    private Path resolveWorkDir() {
        Path configured = Paths.get(mlWorkDir);
        if (configured.isAbsolute() || Files.exists(configured) || !isDefaultPath(mlWorkDir, "../ml", "ml")) {
            return configured.toAbsolutePath().normalize();
        }
        Path projectRootPath = Paths.get("ml");
        if (Files.exists(projectRootPath)) {
            return projectRootPath.toAbsolutePath().normalize();
        }
        return Paths.get("../ml").toAbsolutePath().normalize();
    }

    private boolean isDefaultPath(String value, String... defaults) {
        for (String defaultPath : defaults) {
            if (defaultPath.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private Float extractMetric(String output, Pattern pattern) {
        var matcher = pattern.matcher(output);
        Float value = null;
        while (matcher.find()) {
            value = Float.parseFloat(matcher.group(1));
        }
        return value;
    }

    private String tail(StringBuilder output) {
        return tail(output.toString());
    }

    private String tail(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        int maxLength = 4000;
        return output.length() <= maxLength ? output : output.substring(output.length() - maxLength);
    }

    private record RetrainJob(String status, String modelName, LocalDateTime startedAt, LocalDateTime finishedAt,
                              String message, String outputTail) {
        static RetrainJob idle() {
            return new RetrainJob("IDLE", null, null, null, "暂无训练任务", "");
        }

        RetrainJob finish(String status, String message, String outputTail) {
            return new RetrainJob(status, modelName, startedAt, LocalDateTime.now(), message, outputTail);
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "status", status,
                    "modelName", modelName == null ? "" : modelName,
                    "startedAt", startedAt == null ? "" : startedAt.toString(),
                    "finishedAt", finishedAt == null ? "" : finishedAt.toString(),
                    "message", message == null ? "" : message,
                    "outputTail", outputTail == null ? "" : outputTail
            );
        }
    }
}
