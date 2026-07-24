package com.lovespace.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class MediaFileSystem {
    void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    long usableSpace(Path directory) throws IOException {
        return Files.getFileStore(directory).getUsableSpace();
    }

    void copy(InputStream input, Path target) throws IOException {
        Files.copy(input, target);
    }

    boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }

    boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }
}
