package com.grash.service;

import com.grash.model.File;
import com.grash.model.enums.FileType;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MOD-004B — verifies the stored-XSS mitigation: presigned URLs for non-image attachments carry
 * {@code response-content-disposition=attachment} (forcing download), while images stay inline so
 * previews keep working.
 */
class MinioServiceTest {

    private File file(FileType type) {
        return new File("f", "folder/uuid_f", type, null, false);
    }

    private MinioService configuredService(MinioClient client) {
        MinioService service = new MinioService();
        ReflectionTestUtils.setField(service, "minioClient", client);
        ReflectionTestUtils.setField(service, "minioBucket", "bucket");
        ReflectionTestUtils.setField(service, "minioEndpoint", "http://minio:9000");
        ReflectionTestUtils.setField(service, "minioPublicEndpoint", "");
        return service;
    }

    @Test
    void responseHeaderOverrides_forcesAttachmentForNonImageOnly() {
        assertTrue(MinioService.responseHeaderOverrides(file(FileType.IMAGE)).isEmpty());
        assertEquals("attachment",
                MinioService.responseHeaderOverrides(file(FileType.OTHER)).get("response-content-disposition"));
    }

    @Test
    void generateSignedUrl_nonImage_appliesAttachmentDisposition() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.getPresignedObjectUrl(any())).thenReturn("http://minio:9000/bucket/key");
        MinioService service = configuredService(client);

        service.generateSignedUrl(file(FileType.OTHER), 10);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(client).getPresignedObjectUrl(captor.capture());
        assertTrue(captor.getValue().extraQueryParams().containsEntry("response-content-disposition", "attachment"));
    }

    @Test
    void generateSignedUrl_image_staysInline() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.getPresignedObjectUrl(any())).thenReturn("http://minio:9000/bucket/key");
        MinioService service = configuredService(client);

        service.generateSignedUrl(file(FileType.IMAGE), 10);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(client).getPresignedObjectUrl(captor.capture());
        assertFalse(captor.getValue().extraQueryParams().containsKey("response-content-disposition"));
    }
}
