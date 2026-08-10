package com.los.core.controller;

import com.los.core.model.dto.response.StepExecutionRecordView;
import com.los.core.service.flow.step.StepExecutionReadService;
import com.los.core.service.loan.ApplicationTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Step executions", description = "Recorded flow step executor history and unified timeline")
public class StepExecutionQueryController {

    private final StepExecutionReadService stepExecutionReadService;
    private final ApplicationTimelineService applicationTimelineService;

    @GetMapping("/{applicationId}/step-executions")
    @Operation(summary = "List step execution history for an application (most recent first)")
    public List<StepExecutionRecordView> listStepExecutions(@PathVariable UUID applicationId) {
        return stepExecutionReadService.listStepExecutionRecords(applicationId);
    }

    @GetMapping("/{applicationId}/timeline")
    @Operation(summary = "Unified application timeline (status, actions, process steps)")
    public Map<String, Object> timeline(@PathVariable UUID applicationId) {
        return applicationTimelineService.getTimeline(applicationId);
    }
}
