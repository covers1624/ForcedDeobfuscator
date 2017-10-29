/*
 * (C) 2017 covers1624
 * All Rights Reserved
 */
package net.covers1624.forceddeobf.util;

/**
 * Created by covers1624 on 21/10/2017.
 */
public class Singleton<E> {

    public E e;

    public Singleton(E e) {
        this.e = e;
    }

    public E get() {
        return e;
    }

    public void set(E e) {
        this.e = e;
    }

}
