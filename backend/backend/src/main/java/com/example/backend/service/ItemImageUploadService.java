package com.example.backend.service;

import com.example.backend.dto.ItemImageUploadResponse;
import com.example.backend.entity.Item;
import com.example.backend.exception.ImageUploadException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ItemImageUploadService {

    private static final long DEFAULT_MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ItemRepository itemRepository;
    private final Path uploadDirectory;
    private final String publicUrlBase;
    private final long maxFileSize;

    public ItemImageUploadService(
            ItemRepository itemRepository,
            @Value("${app.upload.directory:uploads}") String uploadDirectory,
            @Value("${app.upload.public-url-base:http://localhost:8080/uploads}") String publicUrlBase,
            @Value("${app.upload.max-file-size:5242880}") long maxFileSize
    ) {
        this.itemRepository = itemRepository;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.publicUrlBase = publicUrlBase.replaceAll("/+$", "");
        this.maxFileSize = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
    }

    @Transactional
    public ItemImageUploadResponse uploadImage(Long itemId, MultipartFile image) throws IOException {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item with id " + itemId + " was not found."));

        if (image == null || image.isEmpty()) {
            throw new ImageUploadException(HttpStatus.BAD_REQUEST, "An image file is required.");
        }
        if (image.getSize() > maxFileSize) {
            throw new ImageUploadException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image file must be 5 MB or smaller.");
        }

        byte[] content = image.getBytes();
        String extension = detectImageExtension(content);
        if (extension == null) {
            throw new ImageUploadException(HttpStatus.BAD_REQUEST,
                    "Unsupported image file. Upload a JPG, JPEG, PNG, or WEBP image.");
        }

        Files.createDirectories(uploadDirectory);
        String filename = "item-" + itemId + "-" + UUID.randomUUID() + "." + extension;
        Path destination = uploadDirectory.resolve(filename).normalize();
        if (!destination.getParent().equals(uploadDirectory)) {
            throw new IllegalStateException("Image upload path is invalid.");
        }

        Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        String imageUrl = publicUrlBase + "/" + filename;
        item.setImageUrl(imageUrl);

        try {
            itemRepository.save(item);
        } catch (RuntimeException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }

        return new ItemImageUploadResponse(itemId, imageUrl, "Image uploaded successfully.");
    }

    private String detectImageExtension(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "jpg";
        }

        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return "png";
        }

        if (content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P') {
            return "webp";
        }

        return null;
    }
}
