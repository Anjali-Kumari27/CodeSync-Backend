package com.codesync.file.service;

import com.codesync.file.dto.CreateFileRequest;
import com.codesync.file.dto.FileResponse;
import com.codesync.file.dto.UpdateFileRequest;
import com.codesync.file.exception.DuplicatePathException;
import com.codesync.file.exception.FileDeletedExcepion;
import com.codesync.file.exception.InvalidFileOperationException;
import com.codesync.file.exception.ResourceNotFoundException;
import com.codesync.file.model.CodeFile;
import com.codesync.file.model.FileType;
import com.codesync.file.repository.CodeFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileService}.
 * Uses Mockito only – no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService Unit Tests")
class FileServiceTest {

    @Mock  CodeFileRepository fileRepository;
    @InjectMocks FileService  fileService;

    // ── Shared fixtures ────────────────────────────────────────────────────
    private static final Long USER_ID    = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long FILE_ID    = 100L;
    private static final Long DIR_ID     = 200L;

    private CodeFile sampleFile() {
        return CodeFile.builder()
                .id(FILE_ID)
                .filePath("src/Main.java")
                .fileName("Main.java")
                .fileType(FileType.FILE)
                .content("public class Main {}")
                .mimeType("text/x-java")
                .sizeBytes(21L)
                .projectId(PROJECT_ID)
                .createdBy(USER_ID)
                .deleted(false)
                .build();
    }

    private CodeFile sampleDir() {
        return CodeFile.builder()
                .id(DIR_ID)
                .filePath("src")
                .fileName("src")
                .fileType(FileType.DIRECTORY)
                .sizeBytes(0L)
                .projectId(PROJECT_ID)
                .createdBy(USER_ID)
                .deleted(false)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createFile()")
    class CreateFile {

        @Test
        @DisplayName("creates a FILE and returns a FileResponse")
        void createFile_success() {
            CreateFileRequest req = new CreateFileRequest();
            req.setProjectId(PROJECT_ID);
            req.setFilePath("src/Main.java");
            req.setFileType(FileType.FILE);
            req.setContent("public class Main {}");

            when(fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(PROJECT_ID, "src/Main.java"))
                    .thenReturn(false);
            when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> {
                CodeFile f = inv.getArgument(0);
                f = CodeFile.builder().id(FILE_ID).filePath(f.getFilePath())
                        .fileName(f.getFileName()).fileType(f.getFileType())
                        .content(f.getContent()).mimeType(f.getMimeType())
                        .sizeBytes(f.getSizeBytes()).projectId(PROJECT_ID)
                        .createdBy(USER_ID).deleted(false).build();
                return f;
            });

            FileResponse resp = fileService.createFile(req, USER_ID);

            assertThat(resp.getId()).isEqualTo(FILE_ID);
            assertThat(resp.getFilePath()).isEqualTo("src/Main.java");
            assertThat(resp.getFileName()).isEqualTo("Main.java");
            assertThat(resp.getMimeType()).isEqualTo("text/x-java");
        }

        @Test
        @DisplayName("throws DuplicatePathException if path already exists")
        void createFile_duplicatePath() {
            CreateFileRequest req = new CreateFileRequest();
            req.setProjectId(PROJECT_ID);
            req.setFilePath("src/Main.java");
            req.setFileType(FileType.FILE);

            when(fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(PROJECT_ID, "src/Main.java"))
                    .thenReturn(true);

            assertThatThrownBy(() -> fileService.createFile(req, USER_ID))
                    .isInstanceOf(DuplicatePathException.class)
                    .hasMessageContaining("src/Main.java");
        }

        @Test
        @DisplayName("throws InvalidFileOperationException if DIRECTORY has content")
        void createDirectory_withContent_throws() {
            CreateFileRequest req = new CreateFileRequest();
            req.setProjectId(PROJECT_ID);
            req.setFilePath("src");
            req.setFileType(FileType.DIRECTORY);
            req.setContent("some content");

            when(fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(PROJECT_ID, "src"))
                    .thenReturn(false);

            assertThatThrownBy(() -> fileService.createFile(req, USER_ID))
                    .isInstanceOf(InvalidFileOperationException.class)
                    .hasMessageContaining("Directories cannot have file content");
        }

        @Test
        @DisplayName("infers MIME type from extension when mimeType not provided")
        void createFile_infersMimeType() {
            CreateFileRequest req = new CreateFileRequest();
            req.setProjectId(PROJECT_ID);
            req.setFilePath("app.py");
            req.setFileType(FileType.FILE);

            when(fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(PROJECT_ID, "app.py"))
                    .thenReturn(false);
            when(fileRepository.save(any())).thenAnswer(inv -> {
                CodeFile f = inv.getArgument(0);
                f = CodeFile.builder().id(1L).filePath(f.getFilePath())
                        .fileName(f.getFileName()).fileType(f.getFileType())
                        .mimeType(f.getMimeType()).sizeBytes(0L)
                        .projectId(PROJECT_ID).createdBy(USER_ID).deleted(false).build();
                return f;
            });

            FileResponse resp = fileService.createFile(req, USER_ID);
            assertThat(resp.getMimeType()).isEqualTo("text/x-python");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getFileById()")
    class GetFile {

        @Test
        @DisplayName("returns file metadata when found")
        void getFile_found() {
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(sampleFile()));

            FileResponse resp = fileService.getFileById(FILE_ID);

            assertThat(resp.getId()).isEqualTo(FILE_ID);
            assertThat(resp.getContent()).isNull(); // content excluded from metadata
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void getFile_notFound() {
            when(fileRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.getFileById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws FileDeletedExcepion when file is deleted")
        void getFile_deleted() {
            CodeFile deleted = sampleFile();
            deleted.setDeleted(true);
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> fileService.getFileById(FILE_ID))
                    .isInstanceOf(FileDeletedExcepion.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getFileWithContent()")
    class GetFileContent {

        @Test
        @DisplayName("returns file with content")
        void returnsContent() {
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(sampleFile()));

            FileResponse resp = fileService.getFileWithContent(FILE_ID);

            assertThat(resp.getContent()).isEqualTo("public class Main {}");
        }

        @Test
        @DisplayName("throws InvalidFileOperationException for DIRECTORY")
        void throwsForDirectory() {
            when(fileRepository.findById(DIR_ID)).thenReturn(Optional.of(sampleDir()));

            assertThatThrownBy(() -> fileService.getFileWithContent(DIR_ID))
                    .isInstanceOf(InvalidFileOperationException.class)
                    .hasMessageContaining("directory");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateFile()")
    class UpdateFile {

        @Test
        @DisplayName("updates content and size")
        void updatesContent() {
            CodeFile file = sampleFile();
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
            when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateFileRequest req = new UpdateFileRequest();
            req.setContent("public class Main { public static void main(String[] args){} }");

            FileResponse resp = fileService.updateFile(FILE_ID, req, USER_ID);

            assertThat(resp.getContent()).isEqualTo(req.getContent());
            assertThat(resp.getSizeBytes()).isEqualTo((long) req.getContent().getBytes().length);
        }

        @Test
        @DisplayName("throws DuplicatePathException on rename collision")
        void throwsOnPathCollision() {
            CodeFile file = sampleFile();
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
            when(fileRepository.existsByProjectIdAndFilePathAndDeletedFalse(
                    PROJECT_ID, "src/Other.java")).thenReturn(true);

            UpdateFileRequest req = new UpdateFileRequest();
            req.setFilePath("src/Other.java");

            assertThatThrownBy(() -> fileService.updateFile(FILE_ID, req, USER_ID))
                    .isInstanceOf(DuplicatePathException.class);
        }

        @Test
        @DisplayName("throws InvalidFileOperationException when setting content on a DIRECTORY")
        void throwsSettingContentOnDirectory() {
            when(fileRepository.findById(DIR_ID)).thenReturn(Optional.of(sampleDir()));

            UpdateFileRequest req = new UpdateFileRequest();
            req.setContent("some content");

            assertThatThrownBy(() -> fileService.updateFile(DIR_ID, req, USER_ID))
                    .isInstanceOf(InvalidFileOperationException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteFile()")
    class DeleteFile {

        @Test
        @DisplayName("soft-deletes a file")
        void softDeletesFile() {
            CodeFile file = sampleFile();
            when(fileRepository.findById(FILE_ID)).thenReturn(Optional.of(file));
            when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            fileService.deleteFile(FILE_ID, USER_ID);

            assertThat(file.isDeleted()).isTrue();
            verify(fileRepository).save(file);
        }

        @Test
        @DisplayName("throws InvalidFileOperationException for directory")
        void throwsForDirectory() {
            when(fileRepository.findById(DIR_ID)).thenReturn(Optional.of(sampleDir()));

            assertThatThrownBy(() -> fileService.deleteFile(DIR_ID, USER_ID))
                    .isInstanceOf(InvalidFileOperationException.class)
                    .hasMessageContaining("directory");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteDirectory()")
    class DeleteDirectory {

        @Test
        @DisplayName("soft-deletes directory and all descendants")
        void softDeletesTree() {
            CodeFile dir = sampleDir();
            CodeFile child = sampleFile();
            child.setParent(dir);

            when(fileRepository.findById(DIR_ID)).thenReturn(Optional.of(dir));
            when(fileRepository.findAllUnderDirectory(PROJECT_ID, "src"))
                    .thenReturn(List.of(child));
            when(fileRepository.saveAll(anyList())).thenReturn(List.of(child));
            when(fileRepository.save(any())).thenReturn(dir);

            var result = fileService.deleteDirectory(DIR_ID, USER_ID);

            assertThat(result.getAffectedCount()).isEqualTo(2); // dir + 1 child
            assertThat(dir.isDeleted()).isTrue();
            assertThat(child.isDeleted()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("MIME type inference")
    class MimeTypeInference {

        @Test
        @DisplayName("infers correct MIME for common extensions")
        void infersMimeTypes() throws Exception {
            // Access private method via service instance reflection
            var method = FileService.class.getDeclaredMethod("inferMimeType", String.class);
            method.setAccessible(true);

            assertThat(method.invoke(fileService, "App.java")).isEqualTo("text/x-java");
            assertThat(method.invoke(fileService, "script.py")).isEqualTo("text/x-python");
            assertThat(method.invoke(fileService, "index.js")).isEqualTo("text/javascript");
            assertThat(method.invoke(fileService, "config.json")).isEqualTo("application/json");
            assertThat(method.invoke(fileService, "README.md")).isEqualTo("text/markdown");
            assertThat(method.invoke(fileService, "unknown.xyz")).isEqualTo("text/plain");
        }
    }
}
