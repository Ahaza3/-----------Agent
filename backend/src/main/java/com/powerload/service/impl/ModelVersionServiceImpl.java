package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.powerload.entity.ModelVersion;
import com.powerload.mapper.ModelVersionMapper;
import com.powerload.service.ModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelVersionServiceImpl implements ModelVersionService {

    private final ModelVersionMapper modelVersionMapper;

    @Override
    public List<ModelVersion> listVersions() {
        return modelVersionMapper.selectList(
                new LambdaQueryWrapper<ModelVersion>()
                        .orderByDesc(ModelVersion::getIsActive)
                        .orderByDesc(ModelVersion::getTrainedAt)
                        .orderByDesc(ModelVersion::getCreatedAt));
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
}
