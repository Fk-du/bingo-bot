package com.bingo.app.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class LocalScreenshotStorage {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<MediaType> ALLOWED_TYPES = Set.of(
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.valueOf("image/webp"));

    private final Path rootDir;

    public LocalScreenshotStorage(@Value("${app.screenshots.dir:./data/screenshots}") String dir) {
        this.rootDir = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create screenshot storage directory: " + rootDir, e);
        }
    }

    /**
     * Stores an uploaded payment screenshot and returns its relative serving name.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 5 MB limit");
        }

        MediaType mediaType = MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        if (!ALLOWED_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("Only JPEG, PNG and WEBP images are allowed");
        }

        String extension = switch (mediaType.getSubtype()) {
            case "jpeg" -> "jpg";
            case "png" -> "png";
            case "webp" -> "webp";
            default -> "bin";
        };
        String filename = UUID.randomUUID() + "." + extension;
        Path target = resolve(filename);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store screenshot {}", filename, e);
            throw new IllegalStateException("Could not store screenshot", e);
        }

        log.debug("Stored screenshot {} ({} bytes)", filename, file.getSize());
        return filename;
    }

    /**
     * Loads a previously stored screenshot. Returns null when it does not exist.
     */
    public StoredScreenshot load(String filename) {
        Path path = resolve(filename);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return new StoredScreenshot(path, mediaTypeFor(filename));
    }

    private Path resolve(String filename) {
        Path path = rootDir.resolve(filename).normalize();
        if (!path.startsWith(rootDir)) {
            throw new IllegalArgumentException("Invalid screenshot name");
        }
        return path;
    }

    private MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record StoredScreenshot(Path path, MediaType mediaType) {}
}
