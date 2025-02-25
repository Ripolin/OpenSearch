/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.index.translog;

import org.opensearch.common.util.concurrent.ReleasableLock;
import org.opensearch.index.shard.ShardId;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * The no-op implementation of {@link TranslogManager} that doesn't perform any operation
 *
 * @opensearch.internal
 */
public class NoOpTranslogManager implements TranslogManager {

    private final Translog.Snapshot emptyTranslogSnapshot;
    private final ReleasableLock readLock;
    private final Runnable ensureOpen;
    private final ShardId shardId;
    private final TranslogStats translogStats;

    public NoOpTranslogManager(
        ShardId shardId,
        ReleasableLock readLock,
        Runnable ensureOpen,
        TranslogStats translogStats,
        Translog.Snapshot emptyTranslogSnapshot
    ) throws IOException {
        this.emptyTranslogSnapshot = emptyTranslogSnapshot;
        this.readLock = readLock;
        this.shardId = shardId;
        this.ensureOpen = ensureOpen;
        this.translogStats = translogStats;
    }

    @Override
    public void rollTranslogGeneration() throws TranslogException {}

    @Override
    public int recoverFromTranslog(TranslogRecoveryRunner translogRecoveryRunner, long localCheckpoint, long recoverUpToSeqNo)
        throws IOException {
        try (ReleasableLock lock = readLock.acquire()) {
            ensureOpen.run();
            try (Translog.Snapshot snapshot = emptyTranslogSnapshot) {
                translogRecoveryRunner.run(snapshot);
            } catch (final Exception e) {
                throw new TranslogException(shardId, "failed to recover from empty translog snapshot", e);
            }
        }
        return emptyTranslogSnapshot.totalOperations();
    }

    @Override
    public boolean isTranslogSyncNeeded() {
        return false;
    }

    @Override
    public boolean ensureTranslogSynced(Stream<Translog.Location> locations) throws IOException {
        return false;
    }

    @Override
    public void syncTranslog() throws IOException {}

    @Override
    public TranslogStats getTranslogStats() {
        return translogStats;
    }

    @Override
    public Translog.Location getTranslogLastWriteLocation() {
        return new Translog.Location(0, 0, 0);
    }

    @Override
    public void trimUnreferencedTranslogFiles() throws TranslogException {}

    @Override
    public boolean shouldRollTranslogGeneration() {
        return false;
    }

    @Override
    public void trimOperationsFromTranslog(long belowTerm, long aboveSeqNo) throws TranslogException {}

    @Override
    public Translog getTranslog() {
        return null;
    }

    @Override
    public void ensureCanFlush() {}

    @Override
    public int restoreLocalHistoryFromTranslog(long processedCheckpoint, TranslogRecoveryRunner translogRecoveryRunner) throws IOException {
        return 0;
    }

    @Override
    public void skipTranslogRecovery() {}
}
