/*
 * Copyright 2014 the original author or authors.
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

package ratpack.exec;

import ratpack.api.NonBlocking;
import ratpack.func.Action;
import ratpack.func.Supplier;
import ratpack.http.client.HttpClient;
import ratpack.launch.LaunchConfig;
import ratpack.promise.Fulfiller;
import ratpack.promise.SuccessOrErrorPromise;
import ratpack.registry.NotInRegistryException;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * An execution context.
 */
public interface ExecContext {

  /**
   * Returns this.
   *
   * @return this.
   */
  ExecContext getContext();

  Supplier<? extends ExecContext> getSupplier();

  LaunchConfig getLaunchConfig();

  @NonBlocking
  void error(Exception exception) throws NotInRegistryException;

  /**
   * Gets the file relative to the contextual {@link ratpack.file.FileSystemBinding}.
   * <p>
   * Shorthand for {@code get(FileSystemBinding.class).file(path)}.
   * <p>
   * The default configuration of Ratpack includes a {@link ratpack.file.FileSystemBinding} in all contexts.
   * A {@link NotInRegistryException} will only be thrown if a very custom service setup is being used.
   *
   * @param path The path to pass to the {@link ratpack.file.FileSystemBinding#file(String)} method.
   * @return The file relative to the contextual {@link ratpack.file.FileSystemBinding}
   * @throws NotInRegistryException if there is no {@link ratpack.file.FileSystemBinding} in the current service
   */
  Path file(String path) throws NotInRegistryException;

  /**
   * The application foreground.
   *
   * @return the application foreground
   * @see ratpack.exec.Foreground
   */
  Foreground getForeground();

  <T> SuccessOrErrorPromise<T> background(Callable<T> backgroundOperation);

  <T> SuccessOrErrorPromise<T> promise(Action<? super Fulfiller<T>> action);

  HttpClient getHttpClient();

}