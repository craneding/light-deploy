package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.service.IDeployRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/deploy-records")
public class DeployRecordController {

    @Autowired
    private IDeployRecordService deployRecordService;

    @GetMapping
    public ResponseEntity<?> getAll(@RequestParam(required = false) Integer taskId) {
        if (taskId != null) {
            QueryWrapper<DeployRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("task_id", taskId);
            return ResponseEntity.ok(deployRecordService.list(queryWrapper));
        }
        return ResponseEntity.ok(deployRecordService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(deployRecordService.getById(id));
    }

    @GetMapping("/{id}/artifacts")
    public ResponseEntity<?> listArtifacts(@PathVariable Integer id) {
        String artifactRoot = System.getProperty("user.dir") + "/.artifacts/" + id;
        Path rootPath = Paths.get(artifactRoot);
        List<String> files = new ArrayList<>();
        
        if (Files.exists(rootPath)) {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                files = paths.filter(Files::isRegularFile)
                             .map(p -> rootPath.relativize(p).toString())
                             .collect(Collectors.toList());
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Error reading artifacts: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{id}/artifacts/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable Integer id, @RequestParam String filePath) {
        String artifactRoot = System.getProperty("user.dir") + "/.artifacts/" + id;
        Path fileToDownload = Paths.get(artifactRoot, filePath).normalize();
        
        // Security check to prevent directory traversal
        if (!fileToDownload.startsWith(Paths.get(artifactRoot).normalize())) {
            return ResponseEntity.badRequest().build();
        }

        File file = fileToDownload.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
