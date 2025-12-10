package com.example.onlyoffice.sdk;

import com.onlyoffice.manager.document.DocumentManager;
import com.onlyoffice.model.common.Format;
import com.onlyoffice.model.documenteditor.config.document.DocumentType;
import com.example.onlyoffice.util.KeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom implementation of ONLYOFFICE DocumentManager
 * Delegates to KeyUtils for document key generation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomDocumentManager implements DocumentManager {

    @Override
    public List<Format> getFormats() {
        // Return null - SDK will use default formats
        return null;
    }

    @Override
    public String getDocumentKey(String fileId, boolean embedded) {
        // Use KeyUtils for consistent document key generation
        // Note: version is managed by DocumentService, here we use fileId as-is
        return KeyUtils.sanitize(fileId);
    }

    @Override
    public String getDocumentName(String fileId) {
        // FileId is the filename in our implementation
        return fileId;
    }

    @Override
    public String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    @Override
    public String getBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }

    @Override
    public DocumentType getDocumentType(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return DocumentType.WORD;
        }

        return switch (extension) {
            case "docx", "doc", "odt", "txt", "rtf", "pdf" -> DocumentType.WORD;
            case "xlsx", "xls", "ods", "csv" -> DocumentType.CELL;
            case "pptx", "ppt", "odp" -> DocumentType.SLIDE;
            default -> DocumentType.WORD;
        };
    }

    @Override
    public boolean isEditable(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return false;
        }
        // Only OOXML formats are editable
        return switch (extension) {
            case "docx", "xlsx", "pptx" -> true;
            default -> false;
        };
    }

    @Override
    public boolean isViewable(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return false;
        }
        // Most document formats are viewable
        return switch (extension) {
            case "docx", "doc", "odt", "txt", "rtf", "pdf",
                 "xlsx", "xls", "ods", "csv",
                 "pptx", "ppt", "odp" -> true;
            default -> false;
        };
    }

    @Override
    public boolean isFillable(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return false;
        }
        // Only PDF forms are fillable
        return "pdf".equals(extension);
    }

    @Override
    public boolean hasAction(String fileName, String action) {
        // Delegate to SDK default implementation
        return false;
    }

    @Override
    public InputStream getNewBlankFile(String extension, Locale locale) {
        // Not implemented - return null to use SDK default
        return null;
    }

    @Override
    public String getDefaultExtension(DocumentType documentType) {
        if (documentType == null) {
            return "docx";
        }
        return switch (documentType) {
            case WORD -> "docx";
            case CELL -> "xlsx";
            case SLIDE -> "pptx";
            default -> "docx";
        };
    }

    @Override
    public String getDefaultConvertExtension(String fileName) {
        DocumentType type = getDocumentType(fileName);
        return getDefaultExtension(type);
    }

    @Override
    public List<String> getConvertExtensionList(String fileName) {
        DocumentType type = getDocumentType(fileName);
        if (type == null) {
            return List.of("docx", "pdf");
        }
        return switch (type) {
            case WORD -> List.of("docx", "pdf", "txt", "rtf");
            case CELL -> List.of("xlsx", "pdf", "csv");
            case SLIDE -> List.of("pptx", "pdf");
            default -> List.of("docx", "pdf");
        };
    }

    @Override
    public Map<String, Boolean> getLossyEditableMap() {
        // Return empty map - no lossy editable formats
        return Map.of();
    }

    @Override
    public List<String> getInsertImageExtensions() {
        return List.of("jpg", "jpeg", "png", "gif", "bmp");
    }

    @Override
    public List<String> getCompareFileExtensions() {
        return List.of("docx", "doc", "odt", "rtf");
    }

    @Override
    public List<String> getMailMergeExtensions() {
        return List.of("xlsx", "xls", "ods", "csv");
    }

    @Override
    public long getMaxFileSize() {
        return 100 * 1024 * 1024; // 100MB (matches FileSecurityService)
    }

    @Override
    public long getMaxConversionFileSize() {
        return 100 * 1024 * 1024; // 100MB
    }

    @Override
    public boolean isForm(InputStream inputStream) {
        // Not implemented - delegate to SDK
        return false;
    }
}
