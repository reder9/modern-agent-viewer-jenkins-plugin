package io.jenkins.plugins.fleetlens.monitoring.callable;

import jenkins.security.MasterToSlaveCallable;
import java.io.File;
import java.io.Serializable;

/**
 * Callable executed on the agent to calculate accurate filesystem storage metrics for both remote root and temp directory.
 */
public class FetchStorageCallable extends MasterToSlaveCallable<FetchStorageCallable.StorageResult, RuntimeException> {

    private static final long serialVersionUID = 1L;

    private final String rootPath;

    public FetchStorageCallable(String rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public StorageResult call() {
        StorageResult result = new StorageResult();

        // 1. Root / Workspace storage
        File rootDir = rootPath != null ? new File(rootPath) : new File(".");
        if (!rootDir.exists()) {
            boolean created = rootDir.mkdirs();
            if (!created && !rootDir.exists()) {
                result.rootAvailable = false;
            }
        }

        try {
            long total = rootDir.getTotalSpace();
            long usable = rootDir.getUsableSpace();
            result.rootTotalBytes = total;
            result.rootFreeBytes = usable;
            result.rootUsedBytes = Math.max(0, total - usable);
            result.rootAvailable = total > 0;
        } catch (SecurityException ignored) {
            result.rootAvailable = false;
        }

        // 2. Temp directory storage
        try {
            String tmpDirPath = System.getProperty("java.io.tmpdir");
            if (tmpDirPath != null) {
                File tmpDir = new File(tmpDirPath);
                if (tmpDir.exists()) {
                    long tmpTotal = tmpDir.getTotalSpace();
                    long tmpUsable = tmpDir.getUsableSpace();
                    result.tempTotalBytes = tmpTotal;
                    result.tempFreeBytes = tmpUsable;
                    result.tempAvailable = tmpTotal > 0;
                }
            }
        } catch (SecurityException ignored) {
            result.tempAvailable = false;
        }

        return result;
    }

    public static class StorageResult implements Serializable {
        private static final long serialVersionUID = 1L;

        public boolean rootAvailable;
        public long rootTotalBytes = -1;
        public long rootUsedBytes = -1;
        public long rootFreeBytes = -1;

        public boolean tempAvailable;
        public long tempTotalBytes = -1;
        public long tempFreeBytes = -1;
    }
}
