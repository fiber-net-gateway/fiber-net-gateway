package io.fiber.net.common.utils;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.FileChannel;

public class FileRegionFactory extends AbstractReferenceCounted {
    private static final Logger log = LoggerFactory.getLogger(FileRegionFactory.class);

    private static final ObjectPool<RefFileRegion> POOL = ObjectPool.newPool(RefFileRegion::new);

    private static final long FILE_OF;
    private static final long POSITION_OF;
    private static final long COUNT_OF;
    private static final long TRANSFERRED_OF;

    static {
        try {
            FILE_OF = UnsafeUtil.fieldOffset(DefaultFileRegion.class.getDeclaredField("file"));
            POSITION_OF = UnsafeUtil.fieldOffset(DefaultFileRegion.class.getDeclaredField("position"));
            COUNT_OF = UnsafeUtil.fieldOffset(DefaultFileRegion.class.getDeclaredField("count"));
            TRANSFERRED_OF = UnsafeUtil.fieldOffset(DefaultFileRegion.class.getDeclaredField("transferred"));
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    private final FileChannel channel;

    public FileRegionFactory(FileChannel channel) {
        this.channel = channel;
    }

    public FileRegion createFileRegion(long position, long count) {
        retain();
        RefFileRegion region = POOL.get();
        region.init(this, position, count);
        return region;
    }

    @Override
    protected void deallocate() {
        try {
            channel.close();
        } catch (Exception e) {
            log.warn("error close a file", e);
        }
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return this;
    }

    private static class RefFileRegion extends DefaultFileRegion {
        private final ObjectPool.Handle<RefFileRegion> handle;
        private FileRegionFactory factory;

        public RefFileRegion(ObjectPool.Handle<RefFileRegion> handle) {
            super(MockFileChannel.INSTANCE, 0, 0);
            this.handle = handle;
        }

        void init(FileRegionFactory factory, long position, long count) {
            setRefCnt(1);
            this.factory = factory;
            UnsafeUtil.setObject(this, FILE_OF, factory.channel);
            UnsafeUtil.setLong(this, POSITION_OF, position);
            UnsafeUtil.setLong(this, COUNT_OF, count);
            UnsafeUtil.setLong(this, TRANSFERRED_OF, 0);
        }

        @Override
        protected void deallocate() {
            UnsafeUtil.setObject(this, FILE_OF, null);
            FileRegionFactory factory = this.factory;
            if (factory != null) {
                this.factory = null;
                factory.release();
            }
            handle.recycle(this);
        }
    }

}
