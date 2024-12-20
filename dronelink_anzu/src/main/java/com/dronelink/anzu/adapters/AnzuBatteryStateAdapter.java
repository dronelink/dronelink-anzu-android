//  AnzuBatteryStateAdapter.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 11/8/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.anzu.adapters;

import com.dronelink.core.DatedValue;
import com.dronelink.core.adapters.BatteryStateAdapter;
import com.dronelink.anzu.AnzuListenerGroup;

import java.util.Date;

import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.KeyTools;

public class AnzuBatteryStateAdapter implements BatteryStateAdapter {
    private final AnzuListenerGroup listeners = new AnzuListenerGroup();

    private Date updated = new Date();
    private Integer chargeRemainingPercent;
    private Integer voltage;

    public AnzuBatteryStateAdapter(final int index) {
        listeners.init(KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, index), (oldValue, newValue) -> {
            updated = new Date();
            chargeRemainingPercent = newValue;
        });
        listeners.init(KeyTools.createKey(BatteryKey.KeyVoltage, index), (oldValue, newValue) -> voltage = newValue);
    }
    public void close() {
        listeners.cancelAll();
    }

    public DatedValue<BatteryStateAdapter> asDatedValue() {
        return new DatedValue<>(this, updated);
    }

    @Override
    public Double getChargeRemainingPercent() {
        final Integer value = chargeRemainingPercent;
        if (value != null) {
            return value.doubleValue() / 100.0;
        }
        return null;
    }

    @Override
    public Double getVoltage() {
        final Integer value = voltage;
        if (value != null) {
            return value.doubleValue() * 0.001;
        }
        return null;
    }
}
