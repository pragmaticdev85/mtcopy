package com.rabbit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

class MTCopyTest {

    @TempDir
    Path sourceDir; // JUnit creates and cleans this up

    @TempDir
    Path targetDir; // JUnit creates and cleans this up

    private MTCopy setupCopier(Path source, Path target) {
        MTCopy copier = new MTCopy();
        // Simulate Picocli initialization for parameters
        // This is a simplified way to set these for testing the call() method directly
        try {
            java.lang.reflect.Field sourceField = MTCopy.class.getDeclaredField("source");
            sourceField.setAccessible(true);
            sourceField.set(copier, source.toFile());

            java.lang.reflect.Field targetField = MTCopy.class.getDeclaredField("target");
            targetField.setAccessible(true);
            targetField.set(copier, target.toFile());

            java.lang.reflect.Field concurrencyField = MTCopy.class.getDeclaredField("concurrency");
            concurrencyField.setAccessible(true);
            // Use a fixed concurrency for tests, e.g., 2, or Runtime.getRuntime().availableProcessors()
            concurrencyField.set(copier, 2); 
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set up MTCopy fields for testing", e);
        }
        return copier;
    }

    @Test
    void testCopySingleFile() throws Exception {
        Path sourceFile = Files.createFile(sourceDir.resolve("testfile.txt"));
        Files.write(sourceFile, "Hello World".getBytes());

        MTCopy copier = setupCopier(sourceDir, targetDir);
        int exitCode = copier.call();

        assertEquals(0, exitCode, "MTCopy should exit with 0 on success.");
        Path targetFile = targetDir.resolve("testfile.txt");
        assertTrue(Files.exists(targetFile), "Target file should exist.");
        assertArrayEquals(Files.readAllBytes(sourceFile), Files.readAllBytes(targetFile), "File content should match.");
    }

    @Test
    void testCopyDirectoryWithMultipleFiles() throws Exception {
        Path file1 = Files.createFile(sourceDir.resolve("file1.txt"));
        Files.write(file1, "Content1".getBytes());
        Path file2 = Files.createFile(sourceDir.resolve("file2.txt"));
        Files.write(file2, "Content2".getBytes());

        MTCopy copier = setupCopier(sourceDir, targetDir);
        int exitCode = copier.call();

        assertEquals(0, exitCode);
        assertTrue(Files.exists(targetDir.resolve("file1.txt")));
        assertArrayEquals(Files.readAllBytes(file1), Files.readAllBytes(targetDir.resolve("file1.txt")));
        assertTrue(Files.exists(targetDir.resolve("file2.txt")));
        assertArrayEquals(Files.readAllBytes(file2), Files.readAllBytes(targetDir.resolve("file2.txt")));
    }

    @Test
    void testCopyDirectoryWithSubdirectories() throws Exception {
        Path subDir = Files.createDirectory(sourceDir.resolve("subdir"));
        Path fileInSourceDir = Files.createFile(sourceDir.resolve("rootfile.txt"));
        Files.write(fileInSourceDir, "Root Content".getBytes());
        Path fileInSubDir = Files.createFile(subDir.resolve("subfile.txt"));
        Files.write(fileInSubDir, "Sub Content".getBytes());

        MTCopy copier = setupCopier(sourceDir, targetDir);
        int exitCode = copier.call();

        assertEquals(0, exitCode);
        assertTrue(Files.exists(targetDir.resolve("rootfile.txt")));
        assertArrayEquals(Files.readAllBytes(fileInSourceDir), Files.readAllBytes(targetDir.resolve("rootfile.txt")));
        
        Path targetSubDir = targetDir.resolve("subdir");
        assertTrue(Files.exists(targetSubDir));
        assertTrue(Files.isDirectory(targetSubDir));
        
        assertTrue(Files.exists(targetSubDir.resolve("subfile.txt")));
        assertArrayEquals(Files.readAllBytes(fileInSubDir), Files.readAllBytes(targetSubDir.resolve("subfile.txt")));
    }

    @Test
    void testCopyEmptyDirectory() throws Exception {
        // sourceDir is already empty by @TempDir creation.
        // We need to ensure MTCopy is called with sourceDir as the source, not a file within it.
        
        MTCopy copier = setupCopier(sourceDir, targetDir);
        // The 'source' parameter for MTCopy is the directory itself.
        // The previous setupCopier sets sourceDir.toFile() as the 'source' field.
        
        int exitCode = copier.call();
        assertEquals(0, exitCode);
        // The targetDir itself is where the contents of sourceDir are copied.
        // So, if sourceDir is empty, targetDir should also effectively represent an empty copied directory.
        // We need to check if targetDir exists and is empty (or only contains what MTCopy might create if it copies the sourceDir folder *into* targetDir).
        // The current MTCopy logic copies contents of sourcePath into destPath.
        // So, targetDir should remain empty if sourceDir is empty.
        try (var stream = Files.list(targetDir)) {
            assertEquals(0, stream.count(), "Target directory should be empty after copying an empty source directory.");
        }
    }
    
    @Test
    void testSourceDirectoryNotFound() throws Exception {
        Path nonExistentSource = sourceDir.resolve("nonexistent");
        // Do not create nonExistentSource
        
        MTCopy copier = setupCopier(nonExistentSource, targetDir);
        int exitCode = copier.call();
        
        assertNotEquals(0, exitCode, "MTCopy should exit with non-zero on source not found.");
        // Also check that target directory remains empty or untouched.
        try (var stream = Files.list(targetDir)) {
            assertEquals(0, stream.count(), "Target directory should be empty when source is not found.");
        }
    }

    @Test
    void testCopyOverwritesExistingFile() throws Exception {
        Path sourceFile = Files.createFile(sourceDir.resolve("overwrite_me.txt"));
        Files.write(sourceFile, "New Content".getBytes());

        Path targetFile = Files.createFile(targetDir.resolve("overwrite_me.txt"));
        Files.write(targetFile, "Old Content".getBytes());

        MTCopy copier = setupCopier(sourceDir, targetDir);
        int exitCode = copier.call();

        assertEquals(0, exitCode);
        assertTrue(Files.exists(targetFile));
        assertArrayEquals("New Content".getBytes(), Files.readAllBytes(targetFile), "Target file should be overwritten with source content.");
    }
}
