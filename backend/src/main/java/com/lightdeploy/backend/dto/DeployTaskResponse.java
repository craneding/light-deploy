package com.lightdeploy.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeployTaskResponse {
    private Integer id;
    private Integer projectId;
    private String projectName;
    private Integer profileId;
    private String profileName;
    private String gitRefType;
    private String gitRef;
    private String status;
    private String logs; // 新增：部署日志/错误信息
    private String operator;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
