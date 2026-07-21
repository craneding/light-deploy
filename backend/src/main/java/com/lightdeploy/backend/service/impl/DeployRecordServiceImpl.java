package com.lightdeploy.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.mapper.DeployRecordMapper;
import com.lightdeploy.backend.service.IDeployRecordService;
import org.springframework.stereotype.Service;

@Service
public class DeployRecordServiceImpl extends ServiceImpl<DeployRecordMapper, DeployRecord> implements IDeployRecordService {
}
