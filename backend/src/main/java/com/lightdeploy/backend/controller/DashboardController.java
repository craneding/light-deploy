package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.DeployTask;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.entity.Project;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
import com.lightdeploy.backend.service.IDeployTaskService;
import com.lightdeploy.backend.service.IDeployRecordService;
import com.lightdeploy.backend.service.IDeployProfileService;
import com.lightdeploy.backend.service.IProjectService;
import com.lightdeploy.backend.service.IServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private IProjectService projectService;

    @Autowired
    private IServerService serverService;

    @Autowired
    private IDeployTaskService deployTaskService;
    
    @Autowired
    private IDeployRecordService deployRecordService;

    @Autowired
    private IDeployProfileService deployProfileService;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Total counts
        long projectCount = projectService.count();
        long serverCount = serverService.count();
        // Since we are showing deployment record stats below, total tasks should represent total deployment records.
        long taskCount = deployRecordService.count();

        stats.put("projectCount", projectCount);
        stats.put("serverCount", serverCount);
        stats.put("taskCount", taskCount);

        // Task status counts (from deploy_records)
        long successTasks = deployRecordService.count(new QueryWrapper<DeployRecord>().eq("status", "SUCCESS"));
        long failedTasks = deployRecordService.count(new QueryWrapper<DeployRecord>().eq("status", "FAILED"));
        long runningTasks = deployRecordService.count(new QueryWrapper<DeployRecord>().eq("status", "RUNNING"));

        stats.put("successTasks", successTasks);
        stats.put("failedTasks", failedTasks);
        stats.put("runningTasks", runningTasks);

        // Recent records (top 5) with project and profile names
        List<DeployRecord> recentRecords = deployRecordService.list(
                new QueryWrapper<DeployRecord>().orderByDesc("created_at").last("LIMIT 5")
        );
        
        List<Map<String, Object>> enrichedRecentRecords = new ArrayList<>();
        for (DeployRecord record : recentRecords) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("id", record.getId());
            recordMap.put("taskId", record.getTaskId());
            if (record.getCommitId() != null && !record.getCommitId().isEmpty()) {
                recordMap.put("gitRefType", "commit");
                recordMap.put("gitRef", record.getCommitId());
            } else {
                recordMap.put("gitRefType", "branch");
                recordMap.put("gitRef", record.getBranch());
            }
            recordMap.put("status", record.getStatus());
            recordMap.put("createdAt", record.getCreatedAt());
            recordMap.put("startTime", record.getStartTime());
            recordMap.put("endTime", record.getEndTime());
            
            if (record.getTriggerUserId() != null) {
                User user = userMapper.selectById(record.getTriggerUserId());
                if (user != null) {
                    recordMap.put("operator", user.getUsername());
                }
            }
            
            // Enrich with project and profile names if task exists
            String projectName = "-";
            String profileName = "-";
            
            if (record.getTaskId() != null) {
                DeployTask task = deployTaskService.getById(record.getTaskId());
                if (task != null) {
                    if (task.getProjectId() != null) {
                        Project project = projectService.getById(task.getProjectId());
                        if (project != null) {
                            projectName = project.getName();
                        }
                    }
                    if (task.getProfileId() != null) {
                        DeployProfile profile = deployProfileService.getById(task.getProfileId());
                        if (profile != null) {
                            profileName = profile.getName();
                        }
                    }
                }
            }
            
            recordMap.put("projectName", projectName);
            recordMap.put("profileName", profileName);
            
            enrichedRecentRecords.add(recordMap);
        }
        
        stats.put("recentTasks", enrichedRecentRecords);

        return ResponseEntity.ok(stats);
    }
}
