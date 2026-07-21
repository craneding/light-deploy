package com.lightdeploy.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightdeploy.backend.entity.Project;
import com.lightdeploy.backend.mapper.ProjectMapper;
import com.lightdeploy.backend.service.IProjectService;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements IProjectService {
}