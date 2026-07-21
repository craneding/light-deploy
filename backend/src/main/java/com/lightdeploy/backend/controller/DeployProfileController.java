package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.service.IDeployProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/profiles")
public class DeployProfileController {

    @Autowired
    private IDeployProfileService deployProfileService;

    @GetMapping
    public ResponseEntity<List<DeployProfile>> getProfiles(@RequestParam(required = false) Integer projectId) {
        QueryWrapper<DeployProfile> queryWrapper = new QueryWrapper<>();
        if (projectId != null) {
            queryWrapper.eq("project_id", projectId);
        }
        return ResponseEntity.ok(deployProfileService.list(queryWrapper));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeployProfile> getProfileById(@PathVariable Integer id) {
        DeployProfile profile = deployProfileService.getById(id);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @PostMapping
    public ResponseEntity<DeployProfile> createProfile(@RequestBody DeployProfile profile) {
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        deployProfileService.save(profile);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeployProfile> updateProfile(@PathVariable Integer id, @RequestBody DeployProfile profile) {
        profile.setId(id);
        profile.setUpdatedAt(LocalDateTime.now());
        deployProfileService.updateById(profile);
        return ResponseEntity.ok(profile);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Integer id) {
        deployProfileService.removeById(id);
        return ResponseEntity.ok().build();
    }
}