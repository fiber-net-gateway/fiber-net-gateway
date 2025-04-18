package io.fiber.net.common.utils;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

class MockFileChannel extends FileChannel {
    static final MockFileChannel INSTANCE = new MockFileChannel();

    @Override
    public int read(ByteBuffer dst) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) {
        throw new UnsupportedOperationException();

    }

    @Override
    public int write(ByteBuffer src) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long position() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileChannel position(long newPosition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileChannel truncate(long size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void force(boolean metaData) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(ByteBuffer dst, long position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer src, long position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void implCloseChannel() {
        throw new UnsupportedOperationException();
    }
}
