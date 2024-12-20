//  AnzuListenerGroup.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 10/5/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.anzu;

import java.util.ArrayList;
import java.util.List;

import dji.sdk.keyvalue.key.DJIKey;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.manager.KeyManager;

public class AnzuListenerGroup {
    private final List<DJIKey<?>> keyListeners = new ArrayList<>();

    public <Result> void init(final DJIKey<Result> key, final CommonCallbacks.KeyListener<Result> callback) {
        synchronized (keyListeners) {
            keyListeners.add(key);
            KeyManager.getInstance().listen(key, this, callback);
        }
    }

    public void cancelAll() {
        synchronized (keyListeners) {
            for (final DJIKey<?> key : keyListeners) {
                KeyManager.getInstance().cancelListen(key, this);
            }
            keyListeners.clear();
        }
    }
}
