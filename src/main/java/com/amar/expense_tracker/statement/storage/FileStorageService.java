package com.amar.expense_tracker.statement.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Storage abstraction so the physical location of uploaded statements (local disk,
 * S3, ...) is swappable without touching business logic. Takes raw content rather
 * than a Spring {@code MultipartFile} since what's actually stored is the
 * already-validated (and, if the source PDF was encrypted, already-decrypted)
 * byte content, not the original upload part.
 */
public interface FileStorageService {

    String store(InputStream content, UUID ownerId) throws IOException;

    InputStream retrieve(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
