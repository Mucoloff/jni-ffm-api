package dev.sweety.mem;

import dev.sweety.data.buffer.panama.SegmentBuffer;
import dev.sweety.data.buffer.panama.SegmentBufferAllocator;
import dev.sweety.math.pool.Acquire;
import dev.sweety.math.pool.Release;

import java.lang.foreign.MemorySegment;

/**
 * Pool of reusable off-heap {@link MemorySegment}s delegating to Sweety's
 * {@link SegmentBufferAllocator#POOLED}.
 */
public final class SegmentPool {
    private final SegmentBufferAllocator allocator;
    private final int defaultSize;

    public SegmentPool(int defaultSize, int maxPerThread) {
        this.defaultSize = defaultSize;
        this.allocator = SegmentBufferAllocator.POOLED;
    }

    @Acquire
    public Lease acquire() {
        return acquire(defaultSize);
    }

    @Acquire
    public Lease acquire(int minSize) {
        return new Lease(allocator.buffer(minSize));
    }

    public static final class Lease implements AutoCloseable {
        private final SegmentBuffer buffer;

        private Lease(SegmentBuffer buffer) {
            this.buffer = buffer;
        }

        public MemorySegment segment() {
            return buffer.segment();
        }

        public int capacity() {
            return buffer.capacity();
        }

        @Release
        @Override
        public void close() {
            buffer.release();
        }
    }
}
