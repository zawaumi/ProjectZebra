package com.zawa.client.registry;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractClientRegistry<T> {
    protected List<T> registered = new ArrayList<>();

    public void register(T item) {
        registered.add(item);
    }

    public void unregister(T item) {
        registered.remove(item);
    }

    public List<T> getRegisteredItems() {
        return registered;
    }
}