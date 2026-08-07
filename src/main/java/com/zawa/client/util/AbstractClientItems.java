package com.zawa.client.util;

import com.zawa.client.registry.AbstractClientRegistry;

public abstract class AbstractClientItems<T> {
    public abstract AbstractClientRegistry<T> getRegistry();
    public abstract void register();
}