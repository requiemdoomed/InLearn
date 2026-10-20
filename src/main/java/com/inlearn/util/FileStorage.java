package com.inlearn.util;

import com.inlearn.exception.StorageException;
import com.inlearn.exception.ValidationException;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Stores uploaded files in the managed storage directory and copies them back out for download.
 *
 * <p>Files are saved under generated names such as {@code submissions/3f2a...e1.pdf}; that
 * <em>storage key</em> is what the database keeps, next to the original file name for display.
 * Generated names avoid collisions and mean a user-supplied file name can never choose where a
 * file is written. Every key is also validated and resolved inside the storage root before use,
 * which blocks path-traversal tricks such as {@code ../../etc/passwd}.
 */
public class FileStorage {

    /** Top-level folders inside the storage directory. */
    public enum Area {
        MATERIALS("materials"),
        SUBMISSIONS("submissions");

        private final String folderName;

        Area(String folderName) {
            this.folderName = folderName;
        }

        public String folderName() {
            return folderName;
        }
    }

    /** Metadata about a file that has just been copied into storage. */
    public record StoredFile(String storageKey, String originalFilename, long sizeBytes) {
    }

    private static final Logger LOGGER = Logger.getLogger(FileStorage.class.getName());
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "^(materials|submissions)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.[a-z0-9]{1,10})?$");
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final Path storageRoot;
    private final long maxUploadBytes;
    private final Set<String> allowedExtensions;

    public FileStorage(Path storageRoot, long maxUploadBytes, Set<String> allowedExtensions) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
        this.allowedExtensions = Set.copyOf(allowedExtensions);
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }

    public Set<String> allowedExtensions() {
        return allowedExtensions;
    }

    /** A human-readable upload limit such as "25 MB". */
    public String maxUploadSizeLabel() {
        return formatSize(maxUploadBytes);
    }

    /**
     * Checks that a file chosen by the user can be uploaded: it must exist, be readable, be
     * non-empty, be within the size limit and have a permitted extension.
     */
    public void validateUpload(Path sourceFile) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new ValidationException("Choose a file to upload.");
        }
        if (!Files.isReadable(sourceFile)) {
            throw new ValidationException("InLearn can't read " + sourceFile.getFileName()
                    + ". Check that the file isn't open in another program and that you have permission to read it.");
        }
        String extension = extensionOf(sourceFile.getFileName().toString());
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw new ValidationException("Files of this type can't be uploaded. Allowed types: "
                    + String.join(", ", allowedExtensions.stream().sorted().toList()) + ".");
        }
        long size = sizeOf(sourceFile);
        if (size == 0) {
            throw new ValidationException("The selected file is empty. Choose a file that has content.");
        }
        if (size > maxUploadBytes) {
            throw new ValidationException("The selected file is " + formatSize(size)
                    + ". The largest file you can upload is " + maxUploadSizeLabel() + ".");
        }
    }

    /**
     * Validates the source file and copies it into storage under a new generated name.
     * The copy is written to a temporary file first and then moved into place, so a failed
     * or interrupted copy never leaves a half-written file under a valid storage key.
     */
    public StoredFile store(Area area, Path sourceFile) {
        validateUpload(sourceFile);
        String originalFilename = cleanOriginalFilename(sourceFile.getFileName().toString());
        String extension = extensionOf(originalFilename);
        String storageKey = area.folderName() + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = resolveKey(storageKey);
        Path temporaryFile = null;
        try {
            Files.createDirectories(target.getParent());
            temporaryFile = Files.createTempFile(target.getParent(), "upload-", ".part");
            Files.copy(sourceFile, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(temporaryFile, target);
            return new StoredFile(storageKey, originalFilename, Files.size(target));
        } catch (IOException e) {
            deleteQuietly(temporaryFile);
            LOGGER.log(Level.WARNING, "Could not store uploaded file in " + storageRoot, e);
            throw new StorageException("The file couldn't be saved to InLearn's storage folder ("
                    + storageRoot + "). Check that the folder exists, is reachable and has free space, "
                    + "then try again.", e);
        }
    }

    /** Copies a stored file to a location the user chose, for example their Downloads folder. */
    public void copyTo(String storageKey, Path destination) {
        Path storedFile = resolveExisting(storageKey);
        try {
            Files.copy(storedFile, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (AccessDeniedException e) {
            throw new StorageException("InLearn doesn't have permission to save to " + destination.getParent()
                    + ". Choose a different folder.", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not copy stored file " + storageKey + " to " + destination, e);
            throw new StorageException("The file couldn't be saved to " + destination + ". Check the folder "
                    + "and available disk space, then try again.", e);
        }
    }

    /** Returns the path of a stored file, failing with a clear message when it is missing. */
    public Path resolveExisting(String storageKey) {
        Path storedFile = resolveKey(storageKey);
        if (!Files.isDirectory(storageRoot)) {
            throw new StorageException("InLearn's storage folder (" + storageRoot + ") isn't available. "
                    + "If it's on a shared or network drive, check that the drive is connected.");
        }
        if (!Files.isRegularFile(storedFile)) {
            throw new StorageException("This file is missing from InLearn's storage folder. "
                    + "Ask the person who uploaded it to upload it again.");
        }
        if (!Files.isReadable(storedFile)) {
            throw new StorageException("InLearn doesn't have permission to read this file from the storage folder.");
        }
        return storedFile;
    }

    /**
     * Deletes a stored file if it exists. Used to clean up replaced or orphaned files, so a
     * failure is logged rather than shown: the database change it follows has already succeeded.
     *
     * @return whether the file is now gone
     */
    public boolean deleteIfExists(String storageKey) {
        try {
            Files.deleteIfExists(resolveKey(storageKey));
            return true;
        } catch (IOException | StorageException e) {
            LOGGER.log(Level.WARNING, "Could not delete stored file " + storageKey, e);
            return false;
        }
    }

    /**
     * Resolves a storage key to a path inside the storage root. Keys that do not match the
     * generated format, or that would resolve outside the root, are rejected.
     */
    Path resolveKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new StorageException("This file reference is invalid, so the file can't be opened.");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new StorageException("This file reference is invalid, so the file can't be opened.");
        }
        return resolved;
    }

    /** Lower-case extension without the dot, or an empty string if there is none. */
    public static String extensionOf(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    /** Formats a byte count for display, for example "1.4 MB" or "820 KB". */
    public static String formatSize(long bytes) {
        if (bytes >= BYTES_PER_MEGABYTE) {
            double megabytes = bytes / (double) BYTES_PER_MEGABYTE;
            return (megabytes == Math.floor(megabytes) ? String.format(Locale.ROOT, "%.0f", megabytes)
                    : String.format(Locale.ROOT, "%.1f", megabytes)) + " MB";
        }
        if (bytes >= 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        return bytes + " bytes";
    }

    /** Keeps only the final file name and strips control characters before it is shown or stored. */
    static String cleanOriginalFilename(String filename) {
        // Drop any folder part, whichever separator the user's operating system used.
        String justTheName = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        String cleaned = justTheName.replaceAll("\\p{Cntrl}", "").strip();
        if (cleaned.isEmpty()) {
            cleaned = "file";
        }
        return cleaned.length() > MAX_ORIGINAL_FILENAME_LENGTH
                ? cleaned.substring(cleaned.length() - MAX_ORIGINAL_FILENAME_LENGTH)
                : cleaned;
    }

    private static void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some network file systems cannot rename atomically; a plain move is the best available.
            Files.move(temporaryFile, target);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (NoSuchFileException e) {
            throw new ValidationException("The selected file no longer exists. Choose it again.");
        } catch (IOException e) {
            throw new ValidationException("InLearn can't read the size of " + file.getFileName() + ".");
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not remove temporary upload file " + file, e);
        }
    }
}
