package com.rabbit;

import picocli.CommandLine;
import picocli.jansi.graalvm.AnsiConsole;

import java.nio.file.*;
import java.io.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

@CommandLine.Command(name = "mtcopy", mixinStandardHelpOptions = true,
        version = "mtcopy 1.0",
        description = "Multithreading Folder Copy")
public class MTCopy implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Source directory path")
    private File source;

    @CommandLine.Parameters(index = "1", description = "Target directory path")
    private File target;

    @CommandLine.Parameters(index = "2", description = "Concurrency level")
    private int concurrency = Runtime.getRuntime().availableProcessors();

    private Path sourcePath;
    private Path destPath;
    private String destPathStr = null;
    private static final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
    public void copyFile(Path source, Path destination) throws IOException {
        System.out.printf("[%15s] Copying [%s] to [%s]%n", Thread.currentThread().getName(), source.toString(), destination.toString());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("[%15s] Copied [%s] to [%s] (Size: %s bytes)%n", Thread.currentThread().getName(), source.toString(), destination.toString(), nf.format(source.toFile().length()));
    }

    public void multiThreadedCopy(Path sourceDir, Path destDir, ExecutorService executor) throws IOException {
        Files.createDirectories(destDir);
        List<Future<Void>> futures = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
            for (Path entry : stream) {
                if (entry.toFile().isDirectory()) {
                    Callable<Void> dirTask = () -> {
                        try {
                            multiThreadedCopy(entry, Paths.get(destDir.toString(), entry.getFileName().toString()), executor);
                        } catch (IOException e) {
                            System.err.printf("[%15s] ERROR processing directory [%s]: %s%n", Thread.currentThread().getName(), entry.toString(), e.getMessage());
                            throw e; // Re-throw
                        }
                        return null;
                    };
                    futures.add(executor.submit(dirTask));
                } else {
                    Callable<Void> copyTask = () -> {
                        Path intendedDestination = destDir.resolve(entry.getFileName());
                        try {
                            copyFile(entry, intendedDestination);
                        } catch (IOException e) {
                            System.err.printf("[%15s] ERROR copying file [%s] to [%s]: %s%n", Thread.currentThread().getName(), entry.toString(), intendedDestination.toString(), e.getMessage());
                            throw e; // Re-throw to be caught by Future.get()
                        }
                        return null; // Callable<Void> needs to return null
                    };
                    futures.add(executor.submit(copyTask));
                }
            }
        }

        for (Future<Void> future : futures) {
            try {
                future.get(); // Wait for task completion and check for exceptions
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
                throw new IOException("Copy operation was interrupted for: " + sourceDir, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause; // Re-throw the original IOException
                } else {
                    throw new IOException("An unexpected error occurred during copy operation for: " + sourceDir, cause);
                }
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        this.sourcePath = Paths.get(source.getPath());
        this.destPath = Paths.get(target.getPath());
        this.destPathStr = destPath.toString();

        ExecutorService executor = Executors.newFixedThreadPool(this.concurrency);
        try {
            multiThreadedCopy(this.sourcePath, this.destPath, executor);
            return 0; // Success
        } catch (IOException e) {
            System.err.println("MTCopy operation failed: " + e.getMessage());
            // e.printStackTrace(); // Optional: for more detailed debugging
            return 1; // Failure
        } catch (InterruptedException e) {
            System.err.println("MTCopy operation was interrupted.");
            Thread.currentThread().interrupt();
            return 1; // Failure
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                     // executor.shutdownNow(); // Optionally force shutdown
                }
            } catch (InterruptedException e) {
                System.err.println("Executor termination was interrupted.");
                Thread.currentThread().interrupt();
                // Although call() might have already returned, this ensures interruption status is set
                // If this finally block executes before call() returns a failure code,
                // it might be complex to force a failure return here.
                // The primary InterruptedException handling in call()'s catch block should handle the return code.
            }
        }
    }

    public static void main(String[] args) {
        int exitCode;
        try (AnsiConsole ansi = AnsiConsole.windowsInstall()) {
            exitCode = new CommandLine(new MTCopy()).execute(args);
        }
        System.exit(exitCode);
    }
}
