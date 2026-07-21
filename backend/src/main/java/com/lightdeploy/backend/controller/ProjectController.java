package com.lightdeploy.backend.controller;

import com.lightdeploy.backend.entity.Project;
import com.lightdeploy.backend.service.IProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private IProjectService projectService;

    @GetMapping
    public ResponseEntity<?> getAllProjects(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        
        QueryWrapper<Project> query = new QueryWrapper<>();
        query.orderByDesc("created_at");
        
        if (StringUtils.hasText(search)) {
            query.and(q -> q.like("name", search.trim()).or().like("description", search.trim()));
        }

        if (page != null && size != null) {
            Page<Project> projectPage = new Page<>(page, size);
            projectService.page(projectPage, query);
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("list", projectPage.getRecords());
            result.put("total", projectPage.getTotal());
            result.put("current", projectPage.getCurrent());
            result.put("size", projectPage.getSize());
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.ok(projectService.list(query));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProjectById(@PathVariable Integer id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(project);
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectService.save(project);
        return ResponseEntity.ok(project);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(@PathVariable Integer id, @RequestBody Project project) {
        project.setId(id);
        project.setUpdatedAt(LocalDateTime.now());
        projectService.updateById(project);
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Integer id) {
        projectService.removeById(id);
        return ResponseEntity.ok().build();
    }
}