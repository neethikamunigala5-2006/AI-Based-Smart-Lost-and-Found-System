package com.example.backend.service;

import com.example.backend.dto.ItemImageUploadResponse;
import com.example.backend.entity.Item;
import com.example.backend.exception.ImageUploadException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemImageUploadServiceTest {

    @TempDir
    Path uploadDirectory;

    @Test
    void savesSupportedImageAndUpdatesItemImageUrl() throws IOException {
        ItemRepository itemRepository = mock(ItemRepository.class);
        Item item = new Item();
        when(itemRepository.findById(42L)).thenReturn(Optional.of(item));
        ItemImageUploadService service = new ItemImageUploadService(
                itemRepository,
                uploadDirectory.toString(),
                "http://localhost:8080/uploads",
                5 * 1024 * 1024
        );
        byte[] pngSignature = {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2
        };
        MockMultipartFile image = new MockMultipartFile(
                "image", "picture.png", "image/png", pngSignature);

        ItemImageUploadResponse response = service.uploadImage(42L, image);

        assertEquals(42L, response.getItemId());
        assertEquals("Image uploaded successfully.", response.getMessage());
        assertEquals(response.getImageUrl(), item.getImageUrl());
        String filename = response.getImageUrl().substring(response.getImageUrl().lastIndexOf('/') + 1);
        assertTrueFileExists(uploadDirectory.resolve(filename), pngSignature);
        verify(itemRepository).save(item);
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
        return new ItemImageUploadService(
                itemRepository,
                uploadDirectory.toString(),
                "http://localhost:8080/uploads",
                maxFileSize
        );
    }

    private void assertTrueFileExists(Path path, byte[] expectedContent) throws IOException {
        assertTrue(Files.exists(path));
        assertArrayEquals(expectedContent, Files.readAllBytes(path));
    }
}
