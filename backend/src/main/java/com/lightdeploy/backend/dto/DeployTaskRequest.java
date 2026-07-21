package com.lightdeploy.backend.dto;

import lombok.Data;

@Data
public class DeployTaskRequest {
    private Integer projectId;
    private Integer profileId;
    private String gitRefType;
    private String gitRef;
}
