package com.rabbit;

import picocli.CommandLine;
import picocli.jansi.graalvm.AnsiConsole;

import java.nio.file.*;
import java.io.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        System.out.printf("[%15s] Copying [%s] into [%s]%n", Thread.currentThread().getName(), source.getFileName(), destination.getFileName());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("[%15s] Copied [%s] into [%s] of length: %s bytes%n", Thread.currentThread().getName(), source.getFileName(), destination.getFileName(), nf.format(source.toFile().length()));
    }

    public void multiThreadedCopy(Path sourceDir, Path destDir, int numThreads) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Files.createDirectories(destDir);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
            for (Path entry : stream) {
                executor.submit(() -> {
                    try {
                        if (entry.toFile().isDirectory()) {
                            multiThreadedCopy(entry, Paths.get(destDir.toString(), entry.getFileName().toString()), 4);
                            return;
                        }
                        copyFile(entry, destDir.resolve(entry.getFileName()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Integer call() throws Exception {
        this.sourcePath = Paths.get(source.getPath());
        this.destPath = Paths.get(target.getPath());
        this.destPathStr = destPath.toString();

        multiThreadedCopy(this.sourcePath, this.destPath, this.concurrency);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode;
        try (AnsiConsole ansi = AnsiConsole.windowsInstall()) {
            exitCode = new CommandLine(new MTCopy()).execute(args);
        }
        System.exit(exitCode);
    }
}
