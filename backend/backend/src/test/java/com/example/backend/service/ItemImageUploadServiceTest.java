package com.example.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.backend.dto.ItemImageUploadResponse;
import com.example.backend.entity.Item;
import com.example.backend.exception.ImageUploadException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemImageUploadServiceTest {

    @Test
    void uploadsSupportedImageAndSavesSecureCloudinaryUrl() throws IOException {
        ItemRepository itemRepository = mock(ItemRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        Item item = new Item();
        String secureUrl = "https://res.cloudinary.com/example/image/upload/item.png";
        when(itemRepository.findById(42L)).thenReturn(Optional.of(item));
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", secureUrl));
        ItemImageUploadService service = service(itemRepository, cloudinary, 5 * 1024 * 1024);
        byte[] pngSignature = {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2
        };
        MockMultipartFile image = new MockMultipartFile(
                "image", "picture.png", "image/png", pngSignature);

        ItemImageUploadResponse response = service.uploadImage(42L, image);

        assertEquals(42L, response.getItemId());
        assertEquals("Image uploaded successfully.", response.getMessage());
        assertEquals(secureUrl, response.getImageUrl());
        assertEquals(secureUrl, item.getImageUrl());
        verify(uploader).upload(any(byte[].class), anyMap());
        verify(itemRepository).save(item);
    }

    @Test
    void rejectsUploadWhenCloudinaryIsNotConfigured() {
        ItemRepository itemRepository = repositoryWithItem();
        ItemImageUploadService service = service(itemRepository, null, 1024);
        MockMultipartFile image = pngImage();

        ImageUploadException exception = assertThrows(
                ImageUploadException.class, () -> service.uploadImage(42L, image));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void doesNotSaveItemWhenCloudinaryUploadFails() throws IOException {
        ItemRepository itemRepository = repositoryWithItem();
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("upload failed"));
        ItemImageUploadService service = service(itemRepository, cloudinary, 1024);

        ImageUploadException exception = assertThrows(
                ImageUploadException.class, () -> service.uploadImage(42L, pngImage()));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void rejectsEmptyFile() {
        ItemRepository itemRepository = repositoryWithItem();
        ItemImageUploadService service = service(itemRepository, 1024);
        MockMultipartFile image = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        ImageUploadException exception = assertThrows(
                ImageUploadException.class, () -> service.uploadImage(42L, image));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void rejectsUnsupportedFileContent() {
        ItemRepository itemRepository = repositoryWithItem();
        ItemImageUploadService service = service(itemRepository, 1024);
        MockMultipartFile image = new MockMultipartFile(
                "image", "not-image.png", "image/png", "not an image".getBytes());

        ImageUploadException exception = assertThrows(
                ImageUploadException.class, () -> service.uploadImage(42L, image));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void rejectsOversizedFile() {
        ItemRepository itemRepository = repositoryWithItem();
        ItemImageUploadService service = service(itemRepository, 2);
        MockMultipartFile image = new MockMultipartFile(
                "image", "large.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        ImageUploadException exception = assertThrows(
                ImageUploadException.class, () -> service.uploadImage(42L, image));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
    }

    @Test
    void reportsMissingItem() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        when(itemRepository.findById(42L)).thenReturn(Optional.empty());
        ItemImageUploadService service = service(itemRepository, 1024);
        MockMultipartFile image = new MockMultipartFile(
                "image", "picture.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThrows(ResourceNotFoundException.class, () -> service.uploadImage(42L, image));
    }

    private ItemRepository repositoryWithItem() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        when(itemRepository.findById(42L)).thenReturn(Optional.of(new Item()));
        return itemRepository;
    }

    private ItemImageUploadService service(ItemRepository itemRepository, long maxFileSize) {
        return service(itemRepository, null, maxFileSize);
    }

    private ItemImageUploadService service(
            ItemRepository itemRepository,
            Cloudinary cloudinary,
            long maxFileSize
    ) {
        return new ItemImageUploadService(
                itemRepository,
                Optional.ofNullable(cloudinary),
                maxFileSize
        );
    }

    private MockMultipartFile pngImage() {
        return new MockMultipartFile(
                "image",
                "picture.png",
                "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2}
        );
    }
}
