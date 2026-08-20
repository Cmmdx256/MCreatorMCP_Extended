package net.mcreator.MCreatorMCP.engine.transaction;

import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Consumer;

public class TransactionManager {

    private static final Logger LOG = LogManager.getLogger("TransactionManager");

    private final Workspace workspace;
    private WorkspaceSnapshot activeSnapshot;

    public TransactionManager(Workspace workspace) {
        this.workspace = workspace;
    }

    public synchronized WorkspaceSnapshot beginTransaction() {
        String id = "txn_" + UUID.randomUUID().toString().substring(0, 8);
        this.activeSnapshot = new WorkspaceSnapshot(id);
        LOG.info("Transaction started: {}", id);
        return this.activeSnapshot;
    }

    public synchronized void commit() {
        if (activeSnapshot != null) {
            LOG.info("Transaction committed successfully: {}", activeSnapshot.getSnapshotId());
            this.activeSnapshot = null;
        }
    }

    public synchronized void rollback() {
        if (activeSnapshot != null) {
            try {
                activeSnapshot.restore(workspace);
                LOG.warn("Transaction rolled back: {}", activeSnapshot.getSnapshotId());
            } finally {
                this.activeSnapshot = null;
            }
        }
    }

    public synchronized <T> T executeInTransaction(TransactionWork<T> work) throws Exception {
        beginTransaction();
        try {
            T result = work.execute(activeSnapshot);
            commit();
            return result;
        } catch (Throwable t) {
            rollback();
            throw new RollbackException("Transaction failed and was rolled back: " + t.getMessage(), t);
        }
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(WorkspaceSnapshot snapshot) throws Exception;
    }
}
