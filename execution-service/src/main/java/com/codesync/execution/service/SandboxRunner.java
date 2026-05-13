package com.codesync.execution.service;

import com.codesync.execution.exception.ExecutionException;
import com.codesync.execution.exception.ExecutionTimeoutException;
import com.codesync.execution.model.ExecutionLanguage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher; import java.util.regex.Pattern;

/**
 * SandboxRunner – executes code in an isolated subprocess with hard limits.
 *
 * Isolation strategy:
 *  1. Write code to a temporary file in a unique temp directory.
 *  2. Build the OS command for the language (compile + run if needed).
 *  3. Start the process with ProcessBuilder (inheriting minimal env).
 *  4. Enforce timeout via Process.waitFor(timeout, unit).
 *  5. Cap stdout/stderr at max-output-size-kb.
 *  6. Delete the temp directory in a finally block.
 *
 * Production note:
 *  For production, replace subprocess execution with Docker containers or
 *  the Piston/Judge0 API. This implementation runs code in the host JVM's OS
 *  process space and is suitable for internal/trusted-user environments only.
 */

@Component
@Slf4j
public class SandboxRunner {

    @Value("${app.execution.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${app.execution.max-output-size-kb:512}")
    private int maxOutputSizeKb;

    public record SandboxResult(
            String stdout,
            String stderr,
            int exitCode,
            long executionTimeMs
    ) {}

    public SandboxResult run(String code, ExecutionLanguage language, String stdin) {
        Path tempDir = null;

        try {
            tempDir = Files.createTempDirectory("codesync_exec_" + UUID.randomUUID());

            String filename =
                    language == ExecutionLanguage.JAVA
                            ? detectJavaMainClass(code) + ".java"
                            : getFilename(language);

            Path codeFile = tempDir.resolve(filename);
            Files.writeString(codeFile, code, StandardCharsets.UTF_8);

            List<String> command = buildCommand(language, codeFile, workDirName(tempDir));

            log.debug("Executing: {}", command);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            pb.environment().clear();

            long start = System.currentTimeMillis();
            Process process = pb.start();

            if (stdin != null && !stdin.isBlank()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                throw new ExecutionTimeoutException(
                        "Execution timed out after " + timeoutSeconds + " seconds."
                );
            }

            String stdout = readCapped(process.getInputStream());
            String stderr = readCapped(process.getErrorStream());
            int exitCode = process.exitValue();

            return new SandboxResult(stdout, stderr, exitCode, elapsed);

        } catch (ExecutionTimeoutException | ExecutionException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Sandbox error: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private String detectJavaMainClass(String code) {
        Pattern classPattern = Pattern.compile(
                "(?:public\\s+)?class\\s+(\\w+)[\\s\\S]*?public\\s+static\\s+void\\s+main\\s*\\(",
                Pattern.MULTILINE
        );

        Matcher m = classPattern.matcher(code);
        if (m.find()) {
            return m.group(1);
        }

        Pattern anyClass = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");
        Matcher m2 = anyClass.matcher(code);
        if (m2.find()) {
            return m2.group(1);
        }

        return "Main";
    }

    private String getFilename(ExecutionLanguage lang) {
        return switch (lang) {
            case JAVA -> "Main.java";
            case PYTHON -> "main.py";
            case JAVASCRIPT -> "main.js";
            case TYPESCRIPT -> "main.ts";
            case C -> "main.c";
            case CPP -> "main.cpp";
            case GO -> "main.go";
            case RUST -> "main.rs";
            case KOTLIN -> "Main.kt";
            case BASH -> "main.sh";
        };
    }

    private String workDirName(Path dir) {
        return dir.toAbsolutePath().toString().replace("\\", "/");
    }

    private List<String> buildCommand(
            ExecutionLanguage lang,
            Path codeFile,
            String dir
    ) {
        String dockerExe =
                "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe";

        String filename = codeFile.getFileName().toString();

        String image;
        String command;

        switch (lang) {

            case PYTHON -> {
                image = "python:3.11-slim";
                command = "python3 " + filename;
            }

            case JAVASCRIPT -> {
                image = "node:20-slim";
                command = "node " + filename;
            }

            case TYPESCRIPT -> {
                image = "node:20-slim";
                command = "npx --yes ts-node " + filename;
            }

            case BASH -> {
                image = "bash:5";
                command = "bash " + filename;
            }

            case GO -> {
                image = "golang:1.21-alpine";
                command = "go run " + filename;
            }

            case JAVA -> {
                image = "eclipse-temurin:21-jdk-alpine";

                String className = filename.replace(".java", "");

                command =
                        "javac " + filename +
                        " && java " + className;
            }

            case KOTLIN -> {
                image = "zenika/kotlin:latest";
                command =
                        "kotlinc " + filename +
                        " -include-runtime -d out.jar && java -jar out.jar";
            }

            case C -> {
                image = "gcc:13";
                command = "gcc " + filename + " -o a.out && ./a.out";
            }

            case CPP -> {
                image = "gcc:13";
                command = "g++ " + filename + " -o a.out && ./a.out";
            }

            case RUST -> {
                image = "rust:slim";
                command = "rustc " + filename + " -o a.out && ./a.out";
            }

            default -> throw new RuntimeException("Unsupported language");
        }

        return List.of(
                dockerExe,
                "run",
                "--rm",
                "--network", "none",
                "--memory", "256m",
                "--cpus", "0.5",
                "-v", dir + ":/sandbox",
                "-w", "/sandbox",
                image,
                "sh",
                "-c",
                command
        );
    }

    private String readCapped(InputStream is) throws IOException {
        int maxBytes = maxOutputSizeKb * 1024;

        byte[] buffer = new byte[maxBytes + 1];
        int read;
        int totalRead = 0;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (
                totalRead < maxBytes &&
                (read = is.read(buffer, 0, Math.min(4096, maxBytes - totalRead))) != -1
        ) {
            baos.write(buffer, 0, read);
            totalRead += read;
        }

        String result = baos.toString(StandardCharsets.UTF_8);

        if (totalRead >= maxBytes) {
            result += "\n[Output truncated at " + maxOutputSizeKb + " KB]";
        }

        return result;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;

        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}

