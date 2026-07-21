package com.lightdeploy.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.mapper.DeployProfileMapper;
import com.lightdeploy.backend.service.IDeployProfileService;
import org.springframework.stereotype.Service;

@Service
public class DeployProfileServiceImpl extends ServiceImpl<DeployProfileMapper, DeployProfile> implements IDeployProfileService {
}