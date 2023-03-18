package no.ntnu.ambulanceallocation.utils;

import java.util.function.Consumer;

/**
 * Represents an operation that accepts three input arguments and returns no result. This is the
 * three-arity specialization of {@link Consumer}. Unlike most other functional interfaces, {@code
 * TriConsumer} is expected to operate via side effects.
 *
 * <p>
 *
 * <p>This is a <a href="package-summary.html">functional interface</a> whose functional method is
 * {@link #accept(Object, Object, Object)}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 * @param <V> the type of the third argument to the operation
 * @see Consumer
 * @since 1.8
 */
@FunctionalInterface
public interface TriConsumer<T, U, V> {

  /**
   * Performs this operation on the given arguments.
   *
   * @param t the first input argument
   * @param u the second input argument
   */
  long accept(T t, U u, V v);
}
