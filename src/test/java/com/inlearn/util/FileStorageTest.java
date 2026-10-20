package com.inlearn.util;

import com.inlearn.exception.StorageException;
import com.inlearn.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageTest {

    private static final long MAX_BYTES = 1024;

    @TempDir
    Path storageRoot;

    @TempDir
    Path userFolder;

    private FileStorage storage() {
        return new FileStorage(storageRoot, MAX_BYTES, Set.of("pdf", "txt"));
    }

    @Test
    void storesACopyUnderAGeneratedNameAndKeepsTheOriginalName() throws Exception {
        Path source = Files.writeString(userFolder.resolve("My Essay.txt"), "hello");
        FileStorage.StoredFile stored = storage().store(FileStorage.Area.SUBMISSIONS, source);

        assertEquals("My Essay.txt", stored.originalFilename());
        assertTrue(stored.storageKey().startsWith("submissions/"));
        assertTrue(stored.storageKey().endsWith(".txt"));
        assertNotEquals("My Essay.txt", stored.storageKey());
        Path copy = storage().resolveExisting(stored.storageKey());
        assertEquals("hello", Files.readString(copy));
    }

    @Test
    void rejectsDisallowedExtensionsEmptyAndOversizedFiles() throws Exception {
        Path script = Files.writeString(userFolder.resolve("run.exe"), "x");
        Path empty = Files.writeString(userFolder.resolve("empty.txt"), "");
        Path large = Files.write(userFolder.resolve("large.pdf"), new byte[(int) MAX_BYTES + 1]);

        assertThrows(ValidationException.class, () -> storage().validateUpload(script));
        assertThrows(ValidationException.class, () -> storage().validateUpload(empty));
        assertThrows(ValidationException.class, () -> storage().validateUpload(large));
    }

    @Test
    void pathTraversalKeysAreRejected() {
        assertThrows(StorageException.class, () -> storage().resolveExisting("../../etc/passwd"));
        assertThrows(StorageException.class, () -> storage().resolveExisting("submissions/../../secret.txt"));
        assertThrows(StorageException.class, () -> storage().resolveExisting("/etc/passwd"));
    }

    @Test
    void missingFileGivesClearError() {
        StorageException error = assertThrows(StorageException.class,
                () -> storage().resolveExisting("materials/123e4567-e89b-12d3-a456-426614174000.pdf"));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void originalFilenameCannotChooseTheStorageLocation() {
        assertEquals("passwd", FileStorage.cleanOriginalFilename("../../etc/passwd"));
        assertFalse(FileStorage.cleanOriginalFilename("bad\u0000name.txt").contains("\u0000"));
    }
}
