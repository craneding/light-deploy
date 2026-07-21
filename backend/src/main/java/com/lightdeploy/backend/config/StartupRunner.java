package com.lightdeploy.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lightdeploy.backend.entity.DeployRecord;
import com.lightdeploy.backend.service.IDeployRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    @Autowired
    private IDeployRecordService deployRecordService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking for incomplete deploy records to mark as FAILED...");
        
        UpdateWrapper<DeployRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("status", "PENDING", "RUNNING")
                     .set("status", "FAILED")
                     .set("end_time", LocalDateTime.now())
                     .setSql("logs = CONCAT(IFNULL(logs, ''), '\n[System] Task was interrupted due to service restart and marked as FAILED.')");
        
        boolean result = deployRecordService.update(updateWrapper);
        if (result) {
            long count = deployRecordService.count(new QueryWrapper<DeployRecord>().eq("status", "FAILED").like("logs", "[System] Task was interrupted"));
            log.info("Successfully marked interrupted tasks as FAILED.");
        } else {
            log.info("No incomplete tasks found.");
        }
    }
}