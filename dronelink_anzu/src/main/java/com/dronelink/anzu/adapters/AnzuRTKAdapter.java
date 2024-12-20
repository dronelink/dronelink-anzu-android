//  AnzuRTKAdapter.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 3/21/23.
//  Copyright © 2023 Dronelink. All rights reserved.
//
package com.dronelink.anzu.adapters;

import android.content.Context;

import com.dronelink.core.DatedValue;
import com.dronelink.core.adapters.RTKAdapter;
import com.dronelink.core.adapters.RTKStateAdapter;
import com.dronelink.core.command.Command;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.rtk.CustomNetworkRTKCommand;
import com.dronelink.core.kernel.command.rtk.CustomNetworkSettingsRTKCommand;
import com.dronelink.core.kernel.command.rtk.CustomNetworkTransmittingRTKCommand;
import com.dronelink.core.kernel.command.rtk.MaintainAccuracyRTKCommand;
import com.dronelink.core.kernel.command.rtk.ModuleRTKCommand;
import com.dronelink.core.kernel.command.rtk.RTKCommand;
import com.dronelink.core.kernel.command.rtk.ReferenceStationSourceRTKCommand;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.enums.RTKReferenceStationSource;
import com.dronelink.anzu.DronelinkAnzu;
import com.dronelink.anzu.R;

import java.util.List;

import dji.sdk.keyvalue.value.rtkbasestation.RTKCustomNetworkSetting;
import dji.v5.manager.aircraft.rtk.RTKCenter;
import dji.v5.manager.interfaces.INetworkRTKManager;
import dji.v5.manager.interfaces.IRTKCenter;

public class AnzuRTKAdapter implements RTKAdapter {
    private final AnzuRTKStateAdapter state;

    public AnzuRTKAdapter(final Context context, final AnzuDroneAdapter drone) {
        this.state = new AnzuRTKStateAdapter(context, drone);
    }

    public void close() {
        state.close();
    }

    public DatedValue<RTKStateAdapter> getState() {
        return state.asDatedValue();
    }

    public List<Message> getStatusMessages() {
        return state.getStatusMessages();
    }

    public CommandError executeCommand(final Context context, final RTKCommand command, final Command.Finisher finished) {
        final IRTKCenter rtkCenter = RTKCenter.getInstance();
        if (rtkCenter == null) {
            return new CommandError(context.getString(R.string.MissionDisengageReason_rtk_unavailable_title));
        }

        if (command instanceof MaintainAccuracyRTKCommand) {
            final boolean target = ((MaintainAccuracyRTKCommand) command).enabled;
            Command.conditionallyExecute(target != state.isMaintainAccuracyEnabled(), finished, () -> {
                //FIXME isn't working?
                rtkCenter.setRTKMaintainAccuracyEnabled(target, DronelinkAnzu.createCompletionCallback(finished));
            });
            return null;
        }

        if (command instanceof ModuleRTKCommand) {
            final boolean target = ((ModuleRTKCommand) command).enabled;
            Command.conditionallyExecute(target != state.isEnabled(), finished, () -> {
                rtkCenter.setAircraftRTKModuleEnabled(target, DronelinkAnzu.createCompletionCallback(finished));
            });
            return null;
        }

        if (command instanceof ReferenceStationSourceRTKCommand) {
            final RTKReferenceStationSource target = ((ReferenceStationSourceRTKCommand) command).referenceStationSource;
            Command.conditionallyExecute(target != state.getReferenceStationSource(), finished, () -> {
                rtkCenter.setRTKReferenceStationSource(DronelinkAnzu.getRTKReferenceStationSource(target), DronelinkAnzu.createCompletionCallback(finished));
            });
            return null;
        }

        if (command instanceof CustomNetworkRTKCommand) {
            return executeCustomNetworkRTKCommand(context, rtkCenter, (CustomNetworkRTKCommand)command, finished);
        }

        return new CommandError(context.getString(R.string.MissionDisengageReason_command_type_unhandled) + ": " + command.type);
    }

    private CommandError executeCustomNetworkRTKCommand(final Context context, final IRTKCenter rtkCenter, final CustomNetworkRTKCommand command, final Command.Finisher finished) {
        final INetworkRTKManager networkRTKManager = rtkCenter.getCustomRTKManager();
        if (networkRTKManager == null) {
            return new CommandError(context.getString(R.string.MissionDisengageReason_rtk_custom_network_unavailable_title));
        }

        if (command instanceof CustomNetworkTransmittingRTKCommand) {
            final boolean target = ((CustomNetworkTransmittingRTKCommand) command).enabled;
            if (target) {
                networkRTKManager.startNetworkRTKService(null, DronelinkAnzu.createCompletionCallback(finished));
            }
            else {
                networkRTKManager.stopNetworkRTKService(DronelinkAnzu.createCompletionCallback(finished));
            }
            return null;
        }

        if (command instanceof CustomNetworkSettingsRTKCommand) {
            final CustomNetworkSettingsRTKCommand target = (CustomNetworkSettingsRTKCommand) command;
            networkRTKManager.setCustomNetworkRTKSettings(new RTKCustomNetworkSetting(target.serverAddress, target.port, target.username, target.password, target.mountPoint));
            finished.execute(null);
            return null;
        }

        return new CommandError(context.getString(R.string.MissionDisengageReason_command_type_unhandled) + ": " + command.type);
    }
}
