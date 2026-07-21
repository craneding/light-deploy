package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.dto.DeployTaskRequest;
import com.lightdeploy.backend.dto.DeployTaskResponse;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.entity.DeployTask;
import com.lightdeploy.backend.service.DeployEngineService;
import com.lightdeploy.backend.service.IDeployRecordService;
import com.lightdeploy.backend.service.IDeployTaskService;
import com.lightdeploy.backend.entity.Project;
import com.lightdeploy.backend.service.IProjectService;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.service.IDeployProfileService;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/deploy-tasks")
public class DeployTaskController {

    @Autowired
    private IDeployTaskService deployTaskService;
    
    @Autowired
    private IDeployRecordService deployRecordService;
    
    @Autowired
    private DeployEngineService deployEngineService;

    @Autowired
    private IProjectService projectService;

    @Autowired
    private IDeployProfileService deployProfileService;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
            
        QueryWrapper<DeployRecord> query = new QueryWrapper<>();
        query.orderByDesc("created_at");
        
        if (search != null && !search.trim().isEmpty()) {
            // Find projects that match search
            List<Project> projects = projectService.list(new QueryWrapper<Project>().like("name", search));
            List<Integer> projectIds = projects.stream().map(Project::getId).collect(Collectors.toList());
            
            if (!projectIds.isEmpty()) {
                // Find tasks for these projects
                List<DeployTask> tasks = deployTaskService.list(new QueryWrapper<DeployTask>().in("project_id", projectIds));
                List<Integer> taskIds = tasks.stream().map(DeployTask::getId).collect(Collectors.toList());
                if (!taskIds.isEmpty()) {
                    query.and(q -> q.in("task_id", taskIds).or().like("branch", search).or().like("commit_id", search));
                } else {
                    query.and(q -> q.like("branch", search).or().like("commit_id", search));
                }
            } else {
                query.and(q -> q.like("branch", search).or().like("commit_id", search));
            }
        }

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeployRecord> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.core.metadata.IPage<DeployRecord> recordPage = deployRecordService.page(pageParam, query);
        
        List<DeployTaskResponse> responses = new ArrayList<>();
        for (DeployRecord record : recordPage.getRecords()) {
            DeployTask task = deployTaskService.getById(record.getTaskId());
            if (task == null) continue;

            DeployTaskResponse response = new DeployTaskResponse();
            response.setId(record.getId());
            response.setProjectId(task.getProjectId());
            Project project = projectService.getById(task.getProjectId());
            if (project != null) {
                response.setProjectName(project.getName());
            }
            response.setProfileId(task.getProfileId());
            DeployProfile profile = deployProfileService.getById(task.getProfileId());
            if (profile != null) {
                response.setProfileName(profile.getName());
            }
            if (record.getCommitId() != null && !record.getCommitId().isEmpty()) {
                response.setGitRefType("commit");
                response.setGitRef(record.getCommitId());
            } else {
                response.setGitRefType("branch");
                response.setGitRef(record.getBranch());
            }
            response.setStatus(record.getStatus());
            response.setLogs(record.getLogs());
            response.setStartTime(record.getStartTime());
            response.setEndTime(record.getEndTime());
            if (record.getTriggerUserId() != null) {
                User user = userMapper.selectById(record.getTriggerUserId());
                if (user != null) {
                    response.setOperator(user.getUsername());
                }
            }
            response.setCreatedAt(record.getCreatedAt());
            responses.add(response);
        }
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("list", responses);
        result.put("total", recordPage.getTotal());
        result.put("current", recordPage.getCurrent());
        result.put("size", recordPage.getSize());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        // id is DeployRecord id in this context
        DeployRecord record = deployRecordService.getById(id);
        if (record == null) return ResponseEntity.notFound().build();
        
        DeployTask task = deployTaskService.getById(record.getTaskId());
        DeployTaskResponse response = new DeployTaskResponse();
        response.setId(record.getId());
        if (task != null) {
            response.setProjectId(task.getProjectId());
            Project project = projectService.getById(task.getProjectId());
            if (project != null) {
                response.setProjectName(project.getName());
            }
            response.setProfileId(task.getProfileId());
            DeployProfile profile = deployProfileService.getById(task.getProfileId());
            if (profile != null) {
                response.setProfileName(profile.getName());
            }
        }
        if (record.getCommitId() != null && !record.getCommitId().isEmpty()) {
            response.setGitRefType("commit");
            response.setGitRef(record.getCommitId());
        } else {
            response.setGitRefType("branch");
            response.setGitRef(record.getBranch());
        }
        response.setStatus(record.getStatus());
        response.setLogs(record.getLogs());
        response.setStartTime(record.getStartTime());
        response.setEndTime(record.getEndTime());
        if (record.getTriggerUserId() != null) {
            User user = userMapper.selectById(record.getTriggerUserId());
            if (user != null) {
                response.setOperator(user.getUsername());
            }
        }
        response.setCreatedAt(record.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody DeployTaskRequest request) {
        // 1. Find or create DeployTask config
        QueryWrapper<DeployTask> query = new QueryWrapper<>();
        query.eq("project_id", request.getProjectId())
             .eq("profile_id", request.getProfileId());
        
        DeployTask task = deployTaskService.getOne(query, false);
        if (task == null) {
            task = new DeployTask();
            task.setProjectId(request.getProjectId());
            task.setProfileId(request.getProfileId());
            task.setName("Deploy-" + request.getProjectId() + "-" + request.getProfileId());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            deployTaskService.save(task);
        }

        // 2. Create execution record
        DeployRecord record = new DeployRecord();
        record.setTaskId(task.getId());
        // 获取当前登录用户ID
        Integer currentUserId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        record.setTriggerUserId(currentUserId);
        if ("commit".equals(request.getGitRefType())) {
            record.setCommitId(request.getGitRef());
        } else {
            record.setBranch(request.getGitRef());
        }
        record.setStatus("PENDING");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        deployRecordService.save(record);

        // 3. Trigger execution asynchronously
        deployEngineService.executeDeploy(task, record);

        DeployTaskResponse response = new DeployTaskResponse();
        response.setId(record.getId());
        response.setProjectId(task.getProjectId());
        response.setProfileId(task.getProfileId());
        response.setGitRefType(request.getGitRefType());
        response.setGitRef(request.getGitRef());
        response.setStatus(record.getStatus());
        if (record.getTriggerUserId() != null) {
            User user = userMapper.selectById(record.getTriggerUserId());
            if (user != null) {
                response.setOperator(user.getUsername());
            }
        }
        response.setCreatedAt(record.getCreatedAt());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stop(@PathVariable Integer id) {
        // Here we just mark as FAILED for now, 
        // real stopping of the script requires killing the process which is more complex
        DeployRecord record = deployRecordService.getById(id);
        if (record != null && "RUNNING".equals(record.getStatus())) {
            record.setStatus("FAILED");
            deployRecordService.updateById(record);
        }
        return ResponseEntity.ok().build();
    }
}
