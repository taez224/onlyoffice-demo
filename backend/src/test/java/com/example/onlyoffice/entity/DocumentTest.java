package com.example.onlyoffice.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Document Entity 단위 테스트")
class DocumentTest {

    @Test
    @DisplayName("Builder 패턴으로 Document 생성 시 기본값이 올바르게 설정된다")
    void builderShouldSetDefaultValues() {
        // given & when
        Document document = Document.builder()
                .fileName("test.docx")
                .fileKey("test-key-123")
                .fileType("docx")
                .documentType("word")
                .fileSize(1024L)
                .storagePath("documents/test.docx")
                .build();

        // then
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getVersion()).isEqualTo(1);
        assertThat(document.getCreatedBy()).isEqualTo("anonymous");
        assertThat(document.getDeletedAt()).isNull();
    }
}
