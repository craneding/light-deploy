package com.lightdeploy.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightdeploy.backend.entity.DeployTask;
import com.lightdeploy.backend.mapper.DeployTaskMapper;
import com.lightdeploy.backend.service.IDeployTaskService;
import org.springframework.stereotype.Service;

@Service
public class DeployTaskServiceImpl extends ServiceImpl<DeployTaskMapper, DeployTask> implements IDeployTaskService {
}
