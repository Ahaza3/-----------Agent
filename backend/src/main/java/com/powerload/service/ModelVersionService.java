package com.powerload.service;

import com.powerload.entity.ModelVersion;

import java.util.List;

/**
 * 预测模型版本管理服务。
 */
public interface ModelVersionService {

    List<ModelVersion> listVersions();

    ModelVersion activate(Long id);
}
