//  AnzuGimbalStateAdapter.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 10/4/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.anzu.adapters;

import com.dronelink.core.Convert;
import com.dronelink.core.DatedValue;
import com.dronelink.core.adapters.GimbalStateAdapter;
import com.dronelink.core.kernel.core.Orientation3;
import com.dronelink.core.kernel.core.enums.GimbalMode;
import com.dronelink.anzu.AnzuListenerGroup;
import com.dronelink.anzu.DronelinkAnzu;

import java.util.Date;

import dji.sdk.keyvalue.key.DJIActionKeyInfo;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.DJIKeyInfo;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.ComponentIndexType;

public class AnzuGimbalStateAdapter implements GimbalStateAdapter {
    private final AnzuListenerGroup listeners = new AnzuListenerGroup();
    private final Date updated = new Date();
    private final ComponentIndexType index;

    private dji.sdk.keyvalue.value.gimbal.GimbalMode mode;
    private Attitude attitude;

    public AnzuGimbalStateAdapter(final ComponentIndexType index) {
        this.index = index;

        listeners.init(createKey(GimbalKey.KeyGimbalMode), (oldValue, newValue) -> mode = newValue);
        listeners.init(createKey(GimbalKey.KeyGimbalAttitude), (oldValue, newValue) -> attitude = newValue);
    }

    public void close() {
        listeners.cancelAll();
    }

    public DatedValue<GimbalStateAdapter> asDatedValue() {
        return new DatedValue<>(this, updated);
    }

    public <T> DJIKey<T> createKey(final DJIKeyInfo<T> keyInfo) {
        return KeyTools.createKey(keyInfo, index);
    }

    public <T, R> DJIKey.ActionKey<T, R> createKey(final DJIActionKeyInfo<T, R> keyInfo) {
        return KeyTools.createKey(keyInfo, index);
    }

    @Override
    public GimbalMode getMode() {
        return DronelinkAnzu.getGimbalMode(mode);
    }

    @Override
    public Orientation3 getOrientation() {
        final Orientation3 orientation = new Orientation3();
        if (attitude != null) {
            orientation.x = Convert.DegreesToRadians(attitude.getPitch());
            orientation.y = Convert.DegreesToRadians(attitude.getRoll());
            orientation.z = Convert.DegreesToRadians(attitude.getYaw());
        }
        return orientation;
    }
}