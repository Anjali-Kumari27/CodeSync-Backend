package com.codesync.file.controller;

import com.codesync.file.dto.CreateFileRequest;
import com.codesync.file.dto.FileResponse;
import com.codesync.file.exception.ResourceNotFoundException;
import com.codesync.file.model.FileType;
import com.codesync.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileController}.
 * Tests controller methods directly without an HTTP stack.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileController Unit Tests")
class FileControllerTest {

    @Mock  FileService fileService;
    @InjectMocks FileController fileController;

    private static final Long USER_ID    = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long FILE_ID    = 100L;

    private FileResponse sampleResponse() {
        return FileResponse.builder()
                .id(FILE_ID)
                .filePath("src/Main.java")
                .fileName("Main.java")
                .fileType(FileType.FILE)
                .mimeType("text/x-java")
                .sizeBytes(20L)
                .projectId(PROJECT_ID)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createFile()")
    class Create {

        @Test
        @DisplayName("returns HTTP 201 on success")
        void returnsCreated() {
            CreateFileRequest req = new CreateFileRequest();
            req.setProjectId(PROJECT_ID);
            req.setFilePath("src/Main.java");
            req.setFileType(FileType.FILE);

            when(fileService.createFile(req, USER_ID)).thenReturn(sampleResponse());

            ResponseEntity<FileResponse> resp = fileController.createFile(req, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getId()).isEqualTo(FILE_ID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getFile()")
    class GetFile {

        @Test
        @DisplayName("returns HTTP 200 with file metadata")
        void returnsOk() {
            when(fileService.getFileById(FILE_ID)).thenReturn(sampleResponse());

            ResponseEntity<FileResponse> resp = fileController.getFile(FILE_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getFilePath()).isEqualTo("src/Main.java");
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException from service")
        void propagatesNotFound() {
            when(fileService.getFileById(999L))
                    .thenThrow(new ResourceNotFoundException("File", "id", 999L));

            assertThatThrownBy(() -> fileController.getFile(999L, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectTree()")
    class GetTree {

        @Test
        @DisplayName("returns list of root-level nodes")
        void returnsTree() {
            when(fileService.getProjectTree(PROJECT_ID))
                    .thenReturn(List.of(sampleResponse()));

            ResponseEntity<List<FileResponse>> resp =
                    fileController.getProjectTree(PROJECT_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteFile()")
    class DeleteFile {

        @Test
        @DisplayName("returns HTTP 204 on success")
        void returnsNoContent() {
            doNothing().when(fileService).deleteFile(FILE_ID, USER_ID);

            ResponseEntity<Void> resp = fileController.deleteFile(FILE_ID, USER_ID);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(fileService).deleteFile(FILE_ID, USER_ID);
        }
    }
}
