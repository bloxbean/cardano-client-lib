package com.bloxbean.cardano.client.transaction.util;

import java.util.ArrayList;
import java.util.Collection;

public class UniqueList<E> extends ArrayList<E> {

    @Override
    public boolean add(E element) {
        if (!this.contains(element)) {
            return super.add(element);
        }
        return false;  // Element already exists, so don't add
    }

    @Override
    public void add(int index, E element) {
        if (!this.contains(element)) {
            super.add(index, element);
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E element : c) {
            if (!this.contains(element)) {
                super.add(element);
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean modified = false;
        for (E element : c) {
            if (!this.contains(element)) {
                super.add(index++, element);
                modified = true;
            }
        }
        return modified;
    }
}

