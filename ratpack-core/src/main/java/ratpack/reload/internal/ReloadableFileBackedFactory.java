/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.reload.internal;

import io.netty.buffer.ByteBuf;
import ratpack.func.Factory;
import ratpack.util.internal.IoUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ratpack.util.ExceptionUtils.uncheck;

public class ReloadableFileBackedFactory<T> implements Factory<T> {

  // Note: we are blocking for IO on the main thread here, but it only really impacts
  // reloadable mode so it's not worth the complication of jumping off the main thread

  private final Path file;
  private final boolean reloadable;
  private final Producer<T> producer;
  private final Releaser<T> releaser;

  private final AtomicReference<FileTime> lastModifiedHolder = new AtomicReference<>(null);
  private final AtomicReference<ByteBuf> contentHolder = new AtomicReference<>();
  private final AtomicReference<T> delegateHolder = new AtomicReference<>(null);
  private final Lock lock = new ReentrantLock();

  static public interface Producer<T> {
    T produce(Path file, ByteBuf bytes) throws Exception;
  }

  static public interface Releaser<T> {
    void release(T thing);
  }

  private static class NullReleaser<T> implements Releaser<T> {
    @Override
    public void release(T thing) {

    }
  }

  public ReloadableFileBackedFactory(Path file, boolean reloadable, Producer<T> producer) {
    this(file, reloadable, producer, new NullReleaser<>());
  }

  public ReloadableFileBackedFactory(Path file, boolean reloadable, Producer<T> producer, Releaser<T> releaser) {
    this.file = file;
    this.reloadable = reloadable;
    this.producer = producer;
    this.releaser = releaser;

    if (!reloadable) {
      try {
        refresh();
      } catch (Exception e) {
        throw uncheck(e);
      }
    }
  }

  public T create() {
    if (!reloadable) {
      return delegateHolder.get();
    }

    // If the file disappeared, wait a little for it to appear
    int i = 10;
    while (!Files.exists(file) && --i > 0) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        throw uncheck(e);
      }
    }

    if (!Files.exists(file)) {
      return null;
    }

    try {
      if (refreshNeeded()) {
        refresh();
      }
    } catch (Exception e) {
      throw uncheck(e);
    }

    return delegateHolder.get();
  }

  private boolean isBytesAreSame() throws IOException {
    lock.lock();
    try {
      ByteBuf existing = contentHolder.get();
      //noinspection SimplifiableIfStatement
      if (existing == null) {
        return false;
      }

      return IoUtils.read(file).equals(existing);
    } finally {
      lock.unlock();
    }
  }

  private boolean refreshNeeded() throws IOException {
    return !Files.getLastModifiedTime(file).equals(lastModifiedHolder.get()) || !isBytesAreSame();
  }

  private void refresh() throws Exception {
    lock.lock();
    try {
      FileTime lastModifiedTime = Files.getLastModifiedTime(file);
      ByteBuf bytes = IoUtils.read(file);

      if (lastModifiedTime.equals(lastModifiedHolder.get()) && bytes.equals(contentHolder.get())) {
        return;
      }

      T previous = delegateHolder.getAndSet(null);
      if (previous != null) {
        releaser.release(previous);
      }
      delegateHolder.set(producer.produce(file, bytes));

      this.lastModifiedHolder.set(lastModifiedTime);
      this.contentHolder.set(bytes);
    } finally {
      lock.unlock();
    }
  }


}
