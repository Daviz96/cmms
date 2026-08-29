package com.grash.service;

import com.grash.factory.StorageServiceFactory;
import com.grash.model.File;
import com.grash.model.enums.FileType;
import com.grash.repository.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MOD-004B — verifies that deleting a {@link File} also removes its storage object(s) (binary +
 * thumbnail), in the order "storage then metadata", without ever blocking metadata deletion on a
 * storage error. Authorization / tenant isolation are enforced upstream (FileController's
 * canBeDeletedBy) and by CompanyAudit @PostLoad and are not changed by this module.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private FileService fileService;

    private File fileWithPaths(String path, String thumbnailPath) {
        File file = new File("name", path, FileType.OTHER, null, false);
        file.setThumbnailPath(thumbnailPath);
        return file;
    }

    @Test
    void delete_removesStorageObjectsThenMetadata() {
        File file = fileWithPaths("folder/uuid_object", "folder/uuid_thumb");
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);

        fileService.delete(1L);

        InOrder ordered = inOrder(storageService, fileRepository);
        ordered.verify(storageService).delete("folder/uuid_object");
        ordered.verify(storageService).delete("folder/uuid_thumb");
        ordered.verify(fileRepository).deleteById(1L);
    }

    @Test
    void delete_whenStorageFails_stillDeletesMetadata() {
        File file = fileWithPaths("folder/uuid_object", null);
        when(fileRepository.findById(2L)).thenReturn(Optional.of(file));
        when(storageServiceFactory.getStorageService()).thenReturn(storageService);
        doThrow(new RuntimeException("storage down")).when(storageService).delete("folder/uuid_object");

        fileService.delete(2L);

        // Metadata is still removed even though the binary could not be deleted.
        verify(fileRepository).deleteById(2L);
    }

    @Test
    void delete_whenFileAbsent_deletesByIdWithoutStorageCall() {
        when(fileRepository.findById(3L)).thenReturn(Optional.empty());

        fileService.delete(3L);

        verify(fileRepository).deleteById(3L);
        verifyNoInteractions(storageServiceFactory);
    }
}
