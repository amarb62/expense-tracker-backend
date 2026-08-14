package com.amar.expense_tracker.statement.service;

import com.amar.expense_tracker.common.exception.BadRequestException;
import com.amar.expense_tracker.statement.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class StatementFileValidator {

    private final UploadProperties uploadProperties;

    /**
     * Validates the upload and, if the PDF is password-protected, decrypts it.
     * Returns the bytes that should actually be stored/processed going forward:
     * plain, never-encrypted PDF bytes. This way the password is used once, here,
     * and never needs to be persisted, logged, or threaded through to the async
     * processing stage.
     */
    public byte[] validateAndDecrypt(MultipartFile file, String password) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A statement file is required");
        }
        if (file.getSize() > uploadProperties.getMaxFileSizeBytes()) {
            throw new BadRequestException("File exceeds the maximum allowed size");
        }
        validateExtension(file.getOriginalFilename());
        validateMimeType(file.getContentType());
        return validateAndDecryptContents(file, password);
    }

    private void validateExtension(String originalFilename) {
        if (originalFilename == null) {
            throw new BadRequestException("File name is required");
        }
        List<String> allowed = Arrays.stream(uploadProperties.getAllowedExtensions().split(","))
                .map(String::trim)
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .toList();
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        boolean matches = allowed.stream().anyMatch(ext -> lowerName.endsWith("." + ext));
        if (!matches) {
            throw new BadRequestException("Unsupported file extension; only PDF files are supported");
        }
    }

    private void validateMimeType(String contentType) {
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new BadRequestException("Unsupported file type; only application/pdf is accepted");
        }
    }

    private byte[] validateAndDecryptContents(MultipartFile file, String password) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Unable to read the uploaded file");
        }

        try (PDDocument document = Loader.loadPDF(bytes, password == null ? "" : password)) {
            if (document.getNumberOfPages() < 1) {
                throw new BadRequestException("The uploaded PDF has no pages");
            }
            if (!document.isEncrypted()) {
                return bytes;
            }
            document.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
            document.save(decrypted);
            return decrypted.toByteArray();
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("The uploaded PDF is password-protected; the correct password is required");
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file is not a valid PDF");
        }
    }
}
