package com.example.backend.service;

import com.example.backend.dto.ItemImageUploadResponse;
import com.example.backend.entity.Item;
import com.example.backend.exception.ImageUploadException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ItemImageUploadService {

    private static final long DEFAULT_MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ItemRepository itemRepository;
    private final long maxFileSize;
    private final Optional<Cloudinary> cloudinary;

    @Autowired
    public ItemImageUploadService(
            ItemRepository itemRepository,
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret,
            @Value("${app.upload.max-file-size:5242880}") long maxFileSize
    ) {
        this(
                itemRepository,
                cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()
                        ? Optional.empty()
                        : Optional.of(new Cloudinary(Map.of(
                                "cloud_name", cloudName,
                                "api_key", apiKey,
                                "api_secret", apiSecret,
                                "secure", true
                        ))),
                maxFileSize
        );
    }

    ItemImageUploadService(
            ItemRepository itemRepository,
            Optional<Cloudinary> cloudinary,
            long maxFileSize
    ) {
        this.itemRepository = itemRepository;
        this.maxFileSize = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
        this.cloudinary = cloudinary;
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

        Cloudinary configuredCloudinary = cloudinary.orElseThrow(() ->
                new ImageUploadException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Image uploads are not configured."));

        String imageUrl;
        try {
            Map<?, ?> uploadResult = configuredCloudinary.uploader().upload(
                    content,
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "folder", "lost-and-found/items",
                            "public_id", "item-" + itemId + "-" + UUID.randomUUID(),
                            "format", extension
                    )
            );
            Object secureUrl = uploadResult.get("secure_url");
            if (!(secureUrl instanceof String url) || !url.startsWith("https://")) {
                throw new ImageUploadException(HttpStatus.BAD_GATEWAY,
                        "Image storage did not return a secure image URL.");
            }
            imageUrl = url;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ImageUploadException imageUploadException) {
                throw imageUploadException;
            }
            throw new ImageUploadException(HttpStatus.BAD_GATEWAY,
                    "Image upload failed. Please try again.");
        }

        item.setImageUrl(imageUrl);
        itemRepository.save(item);

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
