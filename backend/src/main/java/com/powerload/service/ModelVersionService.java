package com.powerload.service;

import com.powerload.entity.ModelVersion;
import com.powerload.entity.ModelTrainingTask;

import java.util.List;
import java.util.Map;

/**
 * 预测模型版本管理服务。
 */
public interface ModelVersionService {

    List<ModelVersion> listVersions();

    ModelVersion activate(Long id);

    List<ModelVersion> syncLocalArtifacts();

    Map<String, Object> startRetrain(String modelName);

    Map<String, Object> retrainStatus();

    List<ModelTrainingTask> trainingHistory();
}
