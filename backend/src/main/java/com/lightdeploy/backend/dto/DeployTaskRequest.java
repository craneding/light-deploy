package com.lightdeploy.backend.dto;

import lombok.Data;

@Data
public class DeployTaskRequest {
    private Integer projectId;
    private Integer profileId;
    private String gitRefType;
    private String gitRef;
    /** 本次任务是否同步到部署目录，NULL 表示沿用项目/环境配置 */
    private Boolean syncToDeployDir;
}
