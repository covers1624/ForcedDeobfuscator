/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.util;

/**
 * Created by covers1624 on 24/10/2017.
 */
@FunctionalInterface
public interface ThrowingBiConsumer<T, U, E extends Throwable> {

    void accept(T t, U u) throws E;
}
