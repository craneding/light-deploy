package com.lightdeploy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.service.IDeployRecordService;
import com.lightdeploy.backend.util.PathUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/deploy-records")
public class DeployRecordController {

    @Autowired
    private IDeployRecordService deployRecordService;

    @Value("${app.artifacts-dir}")
    private String artifactsDir;

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
        String artifactRoot = PathUtils.resolve(artifactsDir) + "/" + id;
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
        String artifactRoot = PathUtils.resolve(artifactsDir) + "/" + id;
        Path fileToDownload = Paths.get(artifactRoot, filePath).normalize();
        
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

    @GetMapping("/{id}/artifacts/download-all")
    public void downloadAllArtifacts(@PathVariable Integer id, HttpServletResponse response) throws IOException {
        String artifactRoot = PathUtils.resolve(artifactsDir) + "/" + id;
        Path rootPath = Paths.get(artifactRoot);

        if (!Files.exists(rootPath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"artifacts-" + id + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    String relativePath = rootPath.relativize(path).toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(relativePath);
                    try {
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to zip artifact: " + relativePath, e);
                    }
                });
            }
        }
    }
}
