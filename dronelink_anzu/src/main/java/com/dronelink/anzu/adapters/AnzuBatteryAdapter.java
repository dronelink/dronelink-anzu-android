//  AnzuBatteryAdapter.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 11/8/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.anzu.adapters;

import android.util.Log;

import com.dronelink.core.DatedValue;
import com.dronelink.core.adapters.BatteryAdapter;
import com.dronelink.core.adapters.BatteryStateAdapter;
import com.dronelink.anzu.AnzuListenerGroup;

import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.KeyTools;

public class AnzuBatteryAdapter implements BatteryAdapter {
    private static final String TAG = AnzuBatteryAdapter.class.getCanonicalName();

    private final int index;
    private String serialNumber;
    private String firmwareVersion;
    private Integer numberOfCells;
    public AnzuBatteryStateAdapter state;

    private final AnzuListenerGroup listeners = new AnzuListenerGroup();

    public AnzuBatteryAdapter(final int index) {
        this.index = index;
        state = new AnzuBatteryStateAdapter(index);

        listeners.init(KeyTools.createKey(BatteryKey.KeySerialNumber), (oldValue, newValue) -> {
            if (newValue != null) {
                serialNumber = newValue;
                Log.i(TAG, "Serial number: " + serialNumber);
            }
        });

        listeners.init(KeyTools.createKey(BatteryKey.KeyFirmwareVersion), (oldValue, newValue) -> {
            if (newValue != null) {
                firmwareVersion = newValue;
                Log.i(TAG, "Firmware version: " + firmwareVersion);
            }
        });

        listeners.init(KeyTools.createKey(BatteryKey.KeyNumberOfCells), (oldValue, newValue) -> numberOfCells = newValue);
    }

    public void close() {
        listeners.cancelAll();
        state.close();
    }

    public DatedValue<BatteryStateAdapter> getState() {
        return state.asDatedValue();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public String getSerialNumber() {
        return serialNumber;
    }

    @Override
    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    @Override
    public Integer getCellCount() {
        return numberOfCells;
    }
}