package dev.sweety.mem;

import dev.sweety.pool.Acquire;
import dev.sweety.pool.ArrayPool;
import dev.sweety.pool.ObjectPool;
import dev.sweety.pool.Release;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Pool of reusable off-heap {@link MemorySegment}s of a fixed capacity. Each lease
 * owns a confined {@link Arena}; {@link Lease#close()} returns it to the pool
 * instead of freeing it, so a hot loop reuses the same native memory rather than
 * calling malloc/free (and allocating GC garbage) on every iteration.
 *
 * <p>Confined arenas have zero locking but must be acquired and released on the
 * same thread — matching {@link ObjectPool#threadLocal}.
 *
 * <p>Mirrors {@code dev.sweety.data.buffer.SegmentBufferAllocator.POOLED}.
 */
public final class SegmentPool {
    private final ArrayPool<Lease> pool;
    private final int defaultSize;

    public SegmentPool(int defaultSize, int maxPerThread) {
        this.defaultSize = defaultSize;
        this.pool = ArrayPool.threadLocal(Lease::new, Lease::capacity, defaultSize, Lease::closeArena, maxPerThread);
    }

    /**
     * Borrow a segment of at least {@code capacity} bytes; close the lease to return it.
     */

    @Acquire
    public Lease acquire() {
        return acquire(defaultSize);
    }

    @Acquire
    public Lease acquire(int minSize) {
        return pool.acquire(minSize);
    }

    public final class Lease implements AutoCloseable {
        private final Arena arena;
        private final MemorySegment segment;
        private final int capacity;

        private Lease(int capacity) {
            this.capacity = capacity;
            this.arena = Arena.ofConfined();
            this.segment = arena.allocate(capacity);
        }

        public MemorySegment segment() {
            return segment;
        }

        public int capacity() {
            return capacity;
        }

        @Release
        @Override
        public void close() {
            pool.release(this); // back to pool; arena stays open for reuse
        }

        private void closeArena() {
            arena.close();
        }
    }
}
