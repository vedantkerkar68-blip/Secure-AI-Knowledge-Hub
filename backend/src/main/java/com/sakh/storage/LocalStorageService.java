package com.sakh.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path rootLocation;

    public LocalStorageService(
    @Value("${app.storage.upload-dir}") String uploadDir
) {
        this.rootLocation = Paths.get(uploadDir)
        .toAbsolutePath()
        .normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFilename.contains("..")) {
            throw new StorageException("Cannot store file with relative path outside current directory");
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String storedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = rootLocation.resolve(storedFilename).normalize();

        if (!destinationFile.getParent().equals(rootLocation)) {
         throw new StorageException("Cannot store file outside current directory");
        }

        try {
            Files.copy(file.getInputStream(), destinationFile);
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }

        return destinationFile.toString();
    }

    @Override
    public Resource load(String storagePath) {
        try {
            Path file = Paths.get(storagePath);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new StorageException("File not found: " + storagePath);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("File not found: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path file = Paths.get(storagePath);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + storagePath, e);
        }
    }
}