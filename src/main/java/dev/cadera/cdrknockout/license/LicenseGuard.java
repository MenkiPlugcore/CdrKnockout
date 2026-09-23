package dev.cadera.cdrknockout.license;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Mandatory local license-integrity guard.
 *
 * This is intentionally an integrity/tamper guard, not an online DRM system.
 */
public final class LicenseGuard {

    private static final String TEMPLATE_RESOURCE = "license-template.txt";
    private static final String LICENSE_FILE_NAME = "LICENSE.txt";
    private static final String INSTALL_MARKER_NAME = ".cdrknockout-license-state.yml";
    private static final String PRODUCT = "CdrKnockout";
    private static final String LICENSE_NAME = "MENKIESTES SOFTWARE LICENSE v1.0";
    private static final String SIGNATURE_SALT = "CADERA-MENKIESTES-CDRKNOCKOUT-LICENSE-GUARD-v1";

    private final CdrKnockoutPlugin plugin;
    private File licenseFile;
    private File markerFile;
    private byte[] templateBytes;
    private String templateHash;
    private BukkitTask monitorTask;
    private boolean violationTriggered;

    public LicenseGuard(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bootstraps the generated license on a legitimate first start, then verifies it.
     * Throws when an existing installation has a missing, altered, or invalid license.
     */
    public void initializeOrThrow() {
        try {
            templateBytes = readTemplate();
            templateHash = sha256(templateBytes);

            File dataFolder = plugin.getDataFolder();
            File pluginsFolder = dataFolder.getParentFile();
            if (pluginsFolder == null) {
                throw new LicenseIntegrityException("Unable to resolve the server plugins directory.");
            }

            licenseFile = new File(dataFolder, LICENSE_FILE_NAME);
            markerFile = new File(pluginsFolder, INSTALL_MARKER_NAME);

            boolean licenseExists = licenseFile.isFile();
            boolean markerExists = markerFile.isFile();

            if (!licenseExists && !markerExists) {
                bootstrapNewInstallation();
            } else if (licenseExists && !markerExists) {
                // Valid copied/migrated installation: accept only if the legal text is untouched.
                verifyLicenseFile();
                writeInstallMarker(UUID.randomUUID().toString(), Instant.now().toString());
                plugin.getLogger().info("License state marker created for existing valid LICENSE.txt.");
            }

            verifyExistingInstallation();
            plugin.getLogger().info("MENKIESTES license integrity: VALID.");
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "CdrKnockout license initialization failed: " + exception.getMessage(),
                    exception
            );
        } catch (LicenseIntegrityException exception) {
            throw new IllegalStateException(
                    "CdrKnockout LICENSE INTEGRITY FAILURE: " + exception.getMessage()
                            + " Restore the original " + LICENSE_FILE_NAME + " before starting the plugin.",
                    exception
            );
        }
    }

    /** Starts a recurring runtime integrity check so deleting/modifying the file while online disables the plugin. */
    public void startMonitoring() {
        stopMonitoring();
        long ticks = Math.max(20L, plugin.getConfig().getLong("production.license-integrity-check-ticks", 100L));
        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (violationTriggered) {
                return;
            }
            try {
                verifyExistingInstallation();
            } catch (Exception exception) {
                violationTriggered = true;
                plugin.getLogger().severe("============================================================");
                plugin.getLogger().severe("CDRKNOCKOUT LICENSE INTEGRITY FAILURE");
                plugin.getLogger().severe(exception.getMessage());
                plugin.getLogger().severe("The plugin will now be disabled.");
                plugin.getLogger().severe("============================================================");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            }
        }, ticks, ticks);
    }

    public void stopMonitoring() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
    }

    public String statusName() {
        try {
            verifyExistingInstallation();
            return "VALID";
        } catch (Exception ignored) {
            return "INVALID";
        }
    }

    public File licenseFile() {
        return licenseFile;
    }

    private void bootstrapNewInstallation() throws IOException, NoSuchAlgorithmException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Unable to create plugin data directory: " + dataFolder.getAbsolutePath());
        }

        atomicWrite(licenseFile.toPath(), templateBytes);
        String installationId = UUID.randomUUID().toString();
        writeInstallMarker(installationId, Instant.now().toString());
        plugin.getLogger().info("Generated required runtime license file: " + licenseFile.getPath());
    }

    private void verifyExistingInstallation()
            throws IOException, NoSuchAlgorithmException, LicenseIntegrityException {
        if (licenseFile == null || markerFile == null || templateHash == null) {
            throw new LicenseIntegrityException("License guard is not initialized.");
        }
        if (!markerFile.isFile()) {
            throw new LicenseIntegrityException("Installation license marker is missing: " + markerFile.getName());
        }
        verifyLicenseFile();
        verifyInstallMarker();
    }

    private void verifyLicenseFile() throws IOException, NoSuchAlgorithmException, LicenseIntegrityException {
        if (!licenseFile.isFile()) {
            throw new LicenseIntegrityException("Required " + LICENSE_FILE_NAME + " is missing from plugins/CdrKnockout/.");
        }
        String currentHash = sha256(Files.readAllBytes(licenseFile.toPath()));
        if (!templateHash.equalsIgnoreCase(currentHash)) {
            throw new LicenseIntegrityException(
                    LICENSE_FILE_NAME + " was modified. Expected SHA-256 " + templateHash
                            + " but found " + currentHash + "."
            );
        }
    }

    private void verifyInstallMarker() throws IOException, LicenseIntegrityException, NoSuchAlgorithmException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(markerFile);
        String product = yaml.getString("product", "");
        String license = yaml.getString("license", "");
        String installationId = yaml.getString("installation-id", "");
        String storedHash = yaml.getString("template-sha256", "");
        String signature = yaml.getString("signature", "");

        if (!PRODUCT.equals(product)) {
            throw new LicenseIntegrityException("Installation marker has an invalid product identifier.");
        }
        if (!LICENSE_NAME.equals(license)) {
            throw new LicenseIntegrityException("Installation marker has an invalid license identifier.");
        }
        if (installationId.isBlank()) {
            throw new LicenseIntegrityException("Installation marker has no installation ID.");
        }
        if (!templateHash.equalsIgnoreCase(storedHash)) {
            throw new LicenseIntegrityException("Installation marker license hash does not match this build.");
        }

        String expectedSignature = stateSignature(installationId, storedHash);
        if (!expectedSignature.equalsIgnoreCase(signature)) {
            throw new LicenseIntegrityException("Installation marker signature is invalid.");
        }
    }

    private void writeInstallMarker(String installationId, String createdAt)
            throws IOException, NoSuchAlgorithmException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("product", PRODUCT);
        yaml.set("license", LICENSE_NAME);
        yaml.set("installation-id", installationId);
        yaml.set("created-at", createdAt);
        yaml.set("template-sha256", templateHash);
        yaml.set("signature", stateSignature(installationId, templateHash));
        yaml.set("notice", "Do not remove or alter plugins/CdrKnockout/" + LICENSE_FILE_NAME + ".");
        atomicWrite(markerFile.toPath(), yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readTemplate() throws IOException {
        try (InputStream stream = plugin.getResource(TEMPLATE_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Bundled license template is missing from the plugin jar.");
            }
            return stream.readAllBytes();
        }
    }

    private String stateSignature(String installationId, String hash) throws NoSuchAlgorithmException {
        return sha256((PRODUCT + "|" + LICENSE_NAME + "|" + installationId + "|" + hash + "|" + SIGNATURE_SALT)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private void atomicWrite(Path target, byte[] content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static final class LicenseIntegrityException extends Exception {
        private LicenseIntegrityException(String message) {
            super(message);
        }
    }
}
