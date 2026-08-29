package com.grash.service;

import com.grash.advancedsearch.SearchCriteria;
import com.grash.advancedsearch.SpecificationBuilder;
import com.grash.factory.StorageServiceFactory;
import com.grash.model.File;
import com.grash.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    private final FileRepository fileRepository;
    private final StorageServiceFactory storageServiceFactory;

    public File create(File File) {
        return fileRepository.save(File);
    }

    public File update(File File) {
        return fileRepository.save(File);
    }

    public Collection<File> getAll() {
        return fileRepository.findAll();
    }

    public void delete(Long id) {
        // MOD-004B — remove the storage object(s) before deleting the metadata so binaries are not
        // orphaned. Authorization and tenant isolation are enforced upstream (FileController's
        // canBeDeletedBy) and by CompanyAudit @PostLoad when the entity is loaded here; this method
        // does not bypass either.
        fileRepository.findById(id).ifPresent(file -> {
            deleteStorageObjectQuietly(file.getPath());
            deleteStorageObjectQuietly(file.getThumbnailPath());
        });
        fileRepository.deleteById(id);
    }

    private void deleteStorageObjectQuietly(String path) {
        if (path == null || path.isBlank()) return;
        try {
            storageServiceFactory.getStorageService().delete(path);
        } catch (Exception e) {
            // Best-effort: never block metadata deletion because the binary could not be removed.
            log.warn("Failed to delete storage object '{}' while deleting file metadata", path, e);
        }
    }

    public Optional<File> findById(Long id) {
        return fileRepository.findById(id);
    }

    public Collection<File> findByCompany(Long id) {
        return fileRepository.findByCompany_Id(id);
    }

    public Page<File> findBySearchCriteria(SearchCriteria searchCriteria) {
        SpecificationBuilder<File> builder = new SpecificationBuilder<>();
        searchCriteria.getFilterFields().forEach(builder::with);
        Pageable page = PageRequest.of(searchCriteria.getPageNum(), searchCriteria.getPageSize(),
                searchCriteria.getDirection(), searchCriteria.getSortField());
        return fileRepository.findAll(builder.build(), page);
    }
}
