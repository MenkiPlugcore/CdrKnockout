package dev.cadera.cdrknockout.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Crash-safer YAML persistence helper.
 *
 * <p>Data is first written to a temporary file in the same directory and then
 * moved over the target. When the filesystem supports atomic replacement we
 * use it; otherwise we fall back to a normal replace operation.</p>
 */
public final class AtomicYamlStorage {

    private AtomicYamlStorage() {
    }

    public static boolean save(YamlConfiguration yaml, File target, Logger logger) {
        File parent = target.getParentFile();
        if (parent == null) {
            logger.severe("Cannot resolve parent directory for " + target.getName());
            return false;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            logger.severe("Could not create data directory: " + parent.getAbsolutePath());
            return false;
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(parent.toPath(), target.getName() + ".", ".tmp");
            yaml.save(temp.toFile());

            try {
                Files.move(
                        temp,
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            logger.severe("Failed to atomically save " + target.getName() + ": " + exception.getMessage());
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            }
        }
    }
}
