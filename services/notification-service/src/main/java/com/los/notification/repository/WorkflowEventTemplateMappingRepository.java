package com.los.notification.repository;

import com.los.notification.entity.WorkflowEventTemplateMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventTemplateMappingRepository extends JpaRepository<WorkflowEventTemplateMapping, UUID> {

    List<WorkflowEventTemplateMapping> findByActiveTrueOrderByWorkflowEventAscChannelAscSortOrderAscTemplateCodeAsc();

    List<WorkflowEventTemplateMapping> findByWorkflowEventAndActiveTrueOrderBySortOrderAscTemplateCodeAsc(
            String workflowEvent);

    List<WorkflowEventTemplateMapping> findByWorkflowEventAndChannelAndActiveTrueOrderBySortOrderAscTemplateCodeAsc(
            String workflowEvent, String channel);
}
