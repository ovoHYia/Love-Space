package com.lovespace;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RemovedMapFeatureGuardTest {
    @Test
    void activeSourcesContainNoMapApiCoordinateFieldsOrLeafletDependency() throws IOException {
        String source;
        try (var frontend = Files.walk(resolve("frontend/src"));
             var backend = Files.walk(resolve("backend/src/main/java"))) {
            source = frontend
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".vue"))
                    .map(RemovedMapFeatureGuardTest::read)
                    .collect(Collectors.joining("\n"))
                    + Files.readString(resolve("frontend/package.json"))
                    + Files.readString(resolve("frontend/package-lock.json"))
                    + backend
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(RemovedMapFeatureGuardTest::read)
                            .collect(Collectors.joining("\n"));
        }

        assertFalse(source.toLowerCase().contains("leaflet"));
        assertFalse(source.matches("(?s)(?i).*\\b(?:latitude|longitude|coordinate|coordinates)\\b.*"));
        assertFalse(source.matches("(?s)(?i).*\\b(?:MemoryMap|MemoryMapView|MapView)\\b.*"));
        assertFalse(source.matches("(?s)(?i).*(?:@(?:Request|Get|Post|Put|Delete)Mapping|fetch\\(|axios\\().{0,100}/map(?:/|\\b).*"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read " + path, ex);
        }
    }

    private static Path resolve(String relative) {
        Path fromRepository = Path.of(relative);
        if (Files.exists(fromRepository)) return fromRepository;
        return Path.of("..", relative);
    }
}
