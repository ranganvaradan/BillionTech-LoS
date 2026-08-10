package com.los.core.controller;

import com.los.core.model.entity.ApplicationNote;
import com.los.core.repository.ApplicationNoteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-2.8: Application notes/comments system.
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/notes")
@RequiredArgsConstructor
@Tag(name = "Application Notes", description = "Notes and comments on loan applications")
public class ApplicationNoteController {

    private final ApplicationNoteRepository noteRepository;

    @PostMapping
    @Operation(summary = "Add a note to an application")
    public ResponseEntity<ApplicationNote> addNote(
            @PathVariable UUID applicationId,
            @RequestBody Map<String, Object> body) {
        ApplicationNote note = ApplicationNote.builder()
                .applicationId(applicationId)
                .authorId(UUID.fromString((String) body.getOrDefault("authorId", UUID.randomUUID().toString())))
                .authorName((String) body.getOrDefault("authorName", "System"))
                .authorRole((String) body.getOrDefault("authorRole", "CREDIT_OFFICER"))
                .content((String) body.get("content"))
                .noteType((String) body.getOrDefault("noteType", "GENERAL"))
                .internal(Boolean.parseBoolean(String.valueOf(body.getOrDefault("internal", false))))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(noteRepository.save(note));
    }

    @GetMapping
    @Operation(summary = "List all notes for an application")
    public ResponseEntity<List<ApplicationNote>> listNotes(
            @PathVariable UUID applicationId,
            @RequestParam(defaultValue = "false") boolean includeInternal) {
        if (includeInternal) {
            return ResponseEntity.ok(noteRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId));
        }
        return ResponseEntity.ok(noteRepository.findByApplicationIdAndInternalFalseOrderByCreatedAtDesc(applicationId));
    }
}
