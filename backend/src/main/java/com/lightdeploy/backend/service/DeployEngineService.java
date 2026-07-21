package com.lightdeploy.backend.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.entity.DeployTask;
import com.lightdeploy.backend.entity.DeployProfile;
import com.lightdeploy.backend.entity.Server;
import com.lightdeploy.backend.entity.User;
import com.lightdeploy.backend.util.PathUtils;
import com.lightdeploy.backend.websocket.DeployLogWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DeployEngineService {

    @Autowired
    private IDeployRecordService deployRecordService;

    @Autowired
    private IServerService serverService;

    @Autowired
    private IDeployProfileService deployProfileService;

    @Autowired
    private IProjectService projectService;
    
    @Autowired
    private com.lightdeploy.backend.mapper.UserMapper userMapper;

    @Autowired
    private DeployLogWebSocketHandler logWebSocketHandler;

    @Value("${gitlab.url}")
    private String gitlabUrl;

    @Value("${app.workspace-dir}")
    private String workspaceDir;

    @Value("${app.artifacts-dir}")
    private String artifactsDir;

    @Async
    public void executeDeploy(DeployTask task, DeployRecord record) {
        String taskIdStr = String.valueOf(record.getId());
        
        try {
            sendLog(taskIdStr, "=== Starting Deployment for Task ID: " + task.getId() + " ===");
            
            record.setStatus("RUNNING");
            record.setStartTime(LocalDateTime.now());
            deployRecordService.updateById(record);

            DeployProfile profile = deployProfileService.getById(task.getProfileId());
            if (profile == null) {
                throw new RuntimeException("Profile not found with ID: " + task.getProfileId());
            }

            Server server = null;
            if (profile.getServerId() != null) {
                server = serverService.getById(profile.getServerId());
                if (server == null) {
                    throw new RuntimeException("Server not found with ID: " + profile.getServerId());
                }
            }
            
            com.lightdeploy.backend.entity.Project project = projectService.getById(task.getProjectId());
            if (project == null) {
                throw new RuntimeException("Project not found with ID: " + task.getProjectId());
            }

            String buildScript = (profile.getBuildScript() != null && !profile.getBuildScript().isEmpty()) 
                                 ? profile.getBuildScript() : project.getBuildScript();
            String buildOutputDir = (profile.getBuildOutputDir() != null && !profile.getBuildOutputDir().isEmpty())
                                 ? profile.getBuildOutputDir() : project.getBuildOutputDir();
            String preScript = (profile.getPreScript() != null && !profile.getPreScript().isEmpty()) 
                                 ? profile.getPreScript() : project.getPreScript();
            Boolean syncToDeployDir = profile.getSyncToDeployDir() != null ? profile.getSyncToDeployDir() : project.getSyncToDeployDir();
            if (syncToDeployDir == null) {
                syncToDeployDir = true; // Default to true if not specified
            }
            String deployDir = (profile.getDeployDir() != null && !profile.getDeployDir().isEmpty()) 
                                 ? profile.getDeployDir() : project.getDeployDir();
            String postScript = (profile.getPostScript() != null && !profile.getPostScript().isEmpty()) 
                                 ? profile.getPostScript() : project.getPostScript();

            // 0. Fetch Gitlab Project Info and Clone/Checkout code
            sendLog(taskIdStr, ">>> 0. Preparing Source Code from GitLab");
            String workspaceDir = prepareSourceCode(project.getGitlabProjectId(), record, taskIdStr);

            // 1. Execute Local Build Script
            if (buildScript != null && !buildScript.isEmpty()) {
                sendLog(taskIdStr, ">>> 1. Executing Local Build Script in " + workspaceDir);
                executeLocalCommand(buildScript, taskIdStr, record, workspaceDir);

                // Save artifacts if buildOutputDir is configured
                if (buildOutputDir != null && !buildOutputDir.trim().isEmpty()) {
                    sendLog(taskIdStr, ">>> 1.1 Saving Build Artifacts from " + buildOutputDir);
                    String artifactRoot = PathUtils.resolve(artifactsDir) + "/" + record.getId();
                    File artifactDir = new File(artifactRoot);
                    if (!artifactDir.exists()) {
                        artifactDir.mkdirs();
                    }
                    // Copy build output to artifact directory
                    String sourcePath = workspaceDir + "/" + buildOutputDir;
                    
                    // Check if the source path is a directory or a file
                    File sourceFile = new File(sourcePath);
                    if (sourceFile.exists()) {
                        if (sourceFile.isDirectory()) {
                            // If it's a directory, copy its contents
                            executeLocalCommand("cp -R " + sourcePath + "/* " + artifactRoot + "/", taskIdStr, record, workspaceDir);
                        } else {
                            // If it's a file, copy the file itself
                            executeLocalCommand("cp " + sourcePath + " " + artifactRoot + "/", taskIdStr, record, workspaceDir);
                        }
                        sendLog(taskIdStr, "Artifacts saved to " + artifactRoot);
                    } else {
                        sendLog(taskIdStr, "[WARNING] Artifact source path does not exist: " + sourcePath);
                    }
                }
            }

            // 2. Execute Pre-script on Remote Server
            if (preScript != null && !preScript.isEmpty()) {
                if (server == null) throw new RuntimeException("Pre-script requires a server but none is associated with this profile.");
                sendLog(taskIdStr, ">>> 2. Executing Pre-Script on Remote Server");
                executeRemoteCommand(server, preScript, taskIdStr, record);
            }

            // 3. Sync files to remote via rsync
            if (syncToDeployDir && deployDir != null && !deployDir.isEmpty()) {
                if (server == null) throw new RuntimeException("Syncing to deploy directory requires a server but none is associated with this profile.");
                sendLog(taskIdStr, ">>> 3. Syncing files via rsync to " + server.getIp() + ":" + deployDir);
                
                String sourcePath = workspaceDir;
                if (buildOutputDir != null && !buildOutputDir.trim().isEmpty()) {
                    sourcePath = workspaceDir + "/" + buildOutputDir;
                }
                
                // Ensure source path ends with / for rsync to copy contents rather than the directory itself
                File sourceFileForRsync = new File(sourcePath);
                if (sourceFileForRsync.isDirectory() && !sourcePath.endsWith("/")) {
                    sourcePath += "/";
                }

                // Create rsync command. We now rely on passwordless SSH.
                int port = server.getPort() != null ? server.getPort() : 22;
                String sshCmd = "ssh -p " + port + " -o StrictHostKeyChecking=no";
                String rsyncCmd = String.format("rsync -avz --delete -e \"%s\" %s %s@%s:%s",
                        sshCmd, sourcePath, server.getUsername(), server.getIp(), deployDir);
                
                executeLocalCommand(rsyncCmd, taskIdStr, record, workspaceDir);
            }

            // 4. Execute Post-script on Remote Server
            if (postScript != null && !postScript.isEmpty()) {
                if (server == null) throw new RuntimeException("Post-script requires a server but none is associated with this profile.");
                sendLog(taskIdStr, ">>> 4. Executing Post-Script on Remote Server");
                executeRemoteCommand(server, postScript, taskIdStr, record);
            }

            sendLog(taskIdStr, "=== Deployment Completed Successfully ===");
            record.setStatus("SUCCESS");
        } catch (Exception e) {
            String errorMsg = "=== Deployment Failed: " + e.getMessage() + " ===";
            sendLog(taskIdStr, errorMsg);
            record.setStatus("FAILED");
            // 将错误信息记录到数据库中
            String currentLogs = record.getLogs() == null ? "" : record.getLogs() + "\n";
            record.setLogs(currentLogs + errorMsg);
        } finally {
            record.setEndTime(LocalDateTime.now());
            deployRecordService.updateById(record);
        }
    }

    private void executeLocalCommand(String command, String taskId, DeployRecord record, String workingDir) throws Exception {
        // Create a temporary script file to execute
        File tempScript = File.createTempFile("deploy_script_", ".sh");
        tempScript.setExecutable(true);
        StringBuilder outputLogs = new StringBuilder();
        try {
            java.nio.file.Files.write(tempScript.toPath(), command.getBytes());
            
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", tempScript.getAbsolutePath());
            if (workingDir != null) {
                File dir = new File(workingDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                pb.directory(dir);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sendLog(taskId, line);
                    outputLogs.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String currentLogs = record.getLogs() == null ? "" : record.getLogs() + "\n";
                record.setLogs(currentLogs + outputLogs.toString() + "\nLocal command exited with code " + exitCode);
                throw new RuntimeException("Local command exited with code " + exitCode);
            }
        } finally {
            tempScript.delete();
        }
    }

    private void executeRemoteCommand(Server server, String command, String taskId, DeployRecord record) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(server.getUsername(), server.getIp(), server.getPort() != null ? server.getPort() : 22);
        session.setPassword(server.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        
        ChannelExec channel = null;
        StringBuilder outputLogs = new StringBuilder();
        try {
            session.connect(10000); // 10 seconds timeout
            
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(null);
            
            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();
            channel.connect();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
            
            String line;
            while (!channel.isClosed()) {
                while ((line = reader.readLine()) != null) {
                    sendLog(taskId, line);
                    outputLogs.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    sendLog(taskId, "[ERROR] " + line);
                    outputLogs.append("[ERROR] ").append(line).append("\n");
                }
                Thread.sleep(100);
            }
            
            // Final read
            while ((line = reader.readLine()) != null) {
                sendLog(taskId, line);
                outputLogs.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                sendLog(taskId, "[ERROR] " + line);
                outputLogs.append("[ERROR] ").append(line).append("\n");
            }

            if (channel.getExitStatus() != 0) {
                String currentLogs = record.getLogs() == null ? "" : record.getLogs() + "\n";
                record.setLogs(currentLogs + outputLogs.toString() + "\nRemote command exited with status " + channel.getExitStatus());
                throw new RuntimeException("Remote command exited with status " + channel.getExitStatus());
            }
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private void sendLog(String taskId, String message) {
        logWebSocketHandler.sendLog(taskId, message);
    }

    private String prepareSourceCode(Integer gitlabProjectId, DeployRecord record, String taskIdStr) throws Exception {
        // 1. Get Project info from GitLab API to get HTTP URL
        // We will just use the first user's token for now, or you should pass the token in context.
        User user = userMapper.selectList(null).stream().filter(u -> u.getAccessToken() != null).findFirst().orElse(null);
        
        String gitlabApiUrl = gitlabUrl + "/api/v4/projects/" + gitlabProjectId;
        
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        // Fallback: If we don't have a user token here, we need a way to authenticate.
        // In a real scenario, the token should be passed down or stored securely per project/user.
        // For demonstration, let's assume we can get a valid user from the DB.
        if (user != null && user.getAccessToken() != null) {
            headers.setBearerAuth(user.getAccessToken());
        } else {
            throw new RuntimeException("No valid GitLab access token found to clone repository.");
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                gitlabApiUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        
        Map<String, Object> projectData = response.getBody();
        if (projectData == null || !projectData.containsKey("http_url_to_repo")) {
            throw new RuntimeException("Could not retrieve repository URL from GitLab.");
        }
        
        String repoUrl = (String) projectData.get("http_url_to_repo");
        // Inject token into URL for basic auth clone (OAuth2 token can be used as username oauth2 with token as password, or simply as password with empty user)
        // Format: https://oauth2:TOKEN@gitlab.example.com/group/project.git
        String token = headers.getFirst(HttpHeaders.AUTHORIZATION).substring(7);
        String authRepoUrl = repoUrl;
        if (authRepoUrl.startsWith("https://")) {
            authRepoUrl = authRepoUrl.replace("https://", "https://oauth2:" + token + "@");
        } else if (authRepoUrl.startsWith("http://")) {
            authRepoUrl = authRepoUrl.replace("http://", "http://oauth2:" + token + "@");
        }

        // 2. Determine workspace directory
        String workspaceRoot = PathUtils.resolve(workspaceDir);
        File rootDir = new File(workspaceRoot);
        if (!rootDir.exists()) {
            boolean created = rootDir.mkdirs();
            if (!created) {
                sendLog(taskIdStr, "Warning: Failed to create workspace root directory: " + workspaceRoot);
            }
        }
        
        String projectName = (String) projectData.get("path");
        String workspaceDir = workspaceRoot + "/" + projectName + "_" + gitlabProjectId;
        File projectDir = new File(workspaceDir);
        File gitDir = new File(workspaceDir, ".git");

        // 3. Clone or Fetch
        if (!projectDir.exists() || !gitDir.exists()) {
            if (projectDir.exists()) {
                sendLog(taskIdStr, "Cleaning up invalid workspace directory...");
                executeLocalCommand("rm -rf " + workspaceDir, taskIdStr, record, workspaceRoot);
            }
            sendLog(taskIdStr, "Cloning repository...");
            executeLocalCommand("git clone " + authRepoUrl + " " + workspaceDir, taskIdStr, record, workspaceRoot);
        } else {
            sendLog(taskIdStr, "Updating remote URL and fetching latest changes...");
            // Update remote URL with the latest token in case it expired or wasn't set correctly before
            executeLocalCommand("git remote set-url origin " + authRepoUrl, taskIdStr, record, workspaceDir);
            executeLocalCommand("git fetch --all", taskIdStr, record, workspaceDir);
        }

        // 4. Checkout the specific ref
        String gitRef = record.getCommitId() != null ? record.getCommitId() : record.getBranch();
        if (gitRef == null || gitRef.isEmpty()) {
            throw new RuntimeException("Git reference (branch/tag/commit) is not specified.");
        }
        sendLog(taskIdStr, "Checking out " + gitRef + "...");
        // Clean up any local changes before checkout
        executeLocalCommand("git reset --hard", taskIdStr, record, workspaceDir);
        executeLocalCommand("git clean -fd", taskIdStr, record, workspaceDir);
        executeLocalCommand("git checkout " + gitRef, taskIdStr, record, workspaceDir);
        
        // If it's a branch, pull the latest
        if (record.getBranch() != null && !record.getBranch().isEmpty() && record.getCommitId() == null) {
             try {
                 executeLocalCommand("git pull origin " + gitRef, taskIdStr, record, workspaceDir);
             } catch (Exception e) {
                 sendLog(taskIdStr, "Warning: git pull failed, it might be a tag or detached HEAD. " + e.getMessage());
             }
        }

        return workspaceDir;
    }
}
