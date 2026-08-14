package com.amar.expense_tracker.statement.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties properties;

    @Override
    public String store(InputStream content, UUID ownerId) throws IOException {
        Path ownerDir = Path.of(properties.getRootDirectory(), ownerId.toString());
        Files.createDirectories(ownerDir);

        String storageFileName = UUID.randomUUID() + ".pdf";
        Path target = ownerDir.resolve(storageFileName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return ownerId + "/" + storageFileName;
    }

    @Override
    public InputStream retrieve(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path root = Path.of(properties.getRootDirectory()).normalize().toAbsolutePath();
        Path resolved = root.resolve(storageKey).normalize().toAbsolutePath();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
