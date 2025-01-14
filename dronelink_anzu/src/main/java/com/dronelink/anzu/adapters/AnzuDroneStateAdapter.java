//  AnzuDroneStateAdapter.java
//  DronelinkAnzu
//
//  Created by Jim McAndrew on 10/4/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.anzu.adapters;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;

import com.dronelink.core.Convert;
import com.dronelink.core.DatedValue;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.kernel.core.DroneObstacleAvoidanceSpecification;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.Orientation3;
import com.dronelink.core.kernel.core.enums.DroneAuxiliaryLightMode;
import com.dronelink.core.kernel.core.enums.DroneLightbridgeFrequencyBand;
import com.dronelink.core.kernel.core.enums.DroneObstacleAvoidanceDirection;
import com.dronelink.core.kernel.core.enums.DroneOcuSyncFrequencyBand;
import com.dronelink.anzu.AnzuListenerGroup;
import com.dronelink.anzu.DronelinkAnzu;
import com.dronelink.anzu.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import dji.sdk.keyvalue.key.AirLinkKey;
import dji.sdk.keyvalue.key.FlightAssistantKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.airlink.ChannelSelectionMode;
import dji.sdk.keyvalue.value.airlink.FrequencyBand;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.common.Velocity3D;
import dji.sdk.keyvalue.value.flightassistant.AuxiliaryLightMode;
import dji.sdk.keyvalue.value.flightcontroller.AirSenseSystemInformation;
import dji.sdk.keyvalue.value.flightcontroller.CompassCalibrationState;
import dji.sdk.keyvalue.value.flightcontroller.CompassState;
import dji.sdk.keyvalue.value.flightcontroller.FCGoHomeState;
import dji.sdk.keyvalue.value.flightcontroller.FailsafeAction;
import dji.sdk.keyvalue.value.flightcontroller.FlightMode;
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel;
import dji.sdk.keyvalue.value.flightcontroller.GoHomeState;
import dji.sdk.keyvalue.value.flightcontroller.WindWarning;
import dji.v5.manager.aircraft.perception.PerceptionManager;
import dji.v5.manager.aircraft.perception.data.ObstacleData;
import dji.v5.manager.aircraft.perception.data.PerceptionInfo;
import dji.v5.manager.aircraft.perception.listener.ObstacleDataListener;
import dji.v5.manager.aircraft.perception.listener.PerceptionInformationListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState;
import dji.v5.manager.diagnostic.DJIDeviceHealthInfo;
import dji.v5.manager.diagnostic.DeviceHealthManager;
import dji.v5.manager.diagnostic.DeviceStatusManager;

public class AnzuDroneStateAdapter implements DroneStateAdapter, PerceptionInformationListener, ObstacleDataListener, WaypointMissionExecuteStateListener {
    private final AnzuListenerGroup listeners = new AnzuListenerGroup();
    private final Context context;
    private final AnzuDroneAdapter drone;
    private Date updated = new Date();
    public FlightMode flightMode;
    private String flightModeString;
    private Integer flightTime;
    private boolean isFlying = false;
    private LocationCoordinate3D coordinate;
    private boolean isHomeLocationSet = false;
    private LocationCoordinate2D homeCoordinate;
    private FCGoHomeState fcGoHomeState;
    private GoHomeState goHomeState;
    private LocationCoordinate3D lastKnownGroundCoordinate;
    private boolean isCompassCalibrating = false;
    private List<CompassState> compassStates;
    private CompassCalibrationState compassCalibrationState;
    private Velocity3D velocity = new Velocity3D();
    private double altitude = 0;
    private Integer ultrasonicAltitude;
    public Integer returnHomeAltitude;
    public Integer maxAltitude;
    public Integer maxDistance;
    public boolean distanceLimitEnabled = false;
    private boolean isNearDistanceLimit = false;
    public boolean isOutOfDistanceLimit = false;
    private boolean isNearHeightLimit = false;
    private Integer batterPercent;
    public Integer lowBatteryThreshold;
    public Integer seriousLowBatteryThreshold;
    private boolean isLowBatteryWarning = false;
    private boolean isSeriousLowBatteryWarning = false;
    private Integer flightTimeRemaining;
    private Attitude attitude;
    private Integer gpsSatellites;
    private GPSSignalLevel gpsSignalLevel;
    private Integer uplinkQuality;
    private Integer downlinkQuality;
    public Integer ocuSyncChannel;
    public ChannelSelectionMode ocuSyncChannelSelectionMode;
    public AuxiliaryLightMode auxiliaryLightModeBottom;
    private FrequencyBand ocuSyncFrequencyBand;
    private WindWarning windWarning;
    private AirSenseSystemInformation airSenseSystemInformation;
    public FailsafeAction failSafeAction;
    private ObstacleData obstacleData;
    private DroneObstacleAvoidanceSpecification obstacleAvoidanceSpecification;
    public boolean landingProtectionEnabled = false;
    public boolean precisionLandingEnabled = false;
    private boolean returnHomeObstacleAvoidanceEnabled = false;
    public boolean visionPositioningEnabled = false;
    private WaypointMissionExecuteState waypointMissionExecuteState;

    public AnzuDroneStateAdapter(final Context context, final AnzuDroneAdapter drone) {
        this.context = context;
        this.drone = drone;
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyFlightMode), (oldValue, newValue) -> flightMode = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyFlightModeString), (oldValue, newValue) -> flightModeString = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyFlightTimeInSeconds), (oldValue, newValue) -> {
            updated = new Date();
            flightTime = newValue;
        });
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsFlying), (oldValue, newValue) -> isFlying = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D), (oldValue, newValue) -> {
            coordinate = newValue;
            if (newValue != null) {
                if (!isFlying) {
                    lastKnownGroundCoordinate = newValue;
                }
            }
        });
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsHomeLocationSet), (oldValue, newValue) -> isHomeLocationSet = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyHomeLocation), (oldValue, newValue) -> homeCoordinate = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyGoHomeState), (oldValue, newValue) -> fcGoHomeState = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyGoHomeStatus), (oldValue, newValue) -> goHomeState = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsCompassCalibrating), (oldValue, newValue) -> isCompassCalibrating = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyCompassState), (oldValue, newValue) -> compassStates = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyCompassCalibrationStatus), (oldValue, newValue) -> compassCalibrationState = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity), (oldValue, newValue) -> velocity = newValue == null ? new Velocity3D() : newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyAltitude), (oldValue, newValue) -> altitude = newValue == null ? 0 : newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyUltrasonicHeight), (oldValue, newValue) -> ultrasonicAltitude = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyGoHomeHeight), (oldValue, newValue) -> returnHomeAltitude = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyHeightLimit), (oldValue, newValue) -> maxAltitude = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyDistanceLimit), (oldValue, newValue) -> maxDistance = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyDistanceLimitEnabled), (oldValue, newValue) -> distanceLimitEnabled = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsNearDistanceLimit), (oldValue, newValue) -> isNearDistanceLimit = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyOutOfDistanceLimit), (oldValue, newValue) -> isOutOfDistanceLimit = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsNearHeightLimit), (oldValue, newValue) -> isNearHeightLimit = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsNearHeightLimit), (oldValue, newValue) -> isNearHeightLimit = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyBatteryPowerPercent), (oldValue, newValue) -> batterPercent = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyLowBatteryWarningThreshold), (oldValue, newValue) -> lowBatteryThreshold = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeySeriousLowBatteryWarningThreshold), (oldValue, newValue) -> seriousLowBatteryThreshold = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsLowBatteryWarning), (oldValue, newValue) -> isLowBatteryWarning = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyIsSeriousLowBatteryWarning), (oldValue, newValue) -> isSeriousLowBatteryWarning = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyRemainingFlightTime), (oldValue, newValue) -> flightTimeRemaining = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude), (oldValue, newValue) -> {
            updated = new Date();
            attitude = newValue;
        });
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount), (oldValue, newValue) -> gpsSatellites = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel), (oldValue, newValue) -> gpsSignalLevel = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyWindWarning), (oldValue, newValue) -> windWarning = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyAirSenseSystemInformation), (oldValue, newValue) -> airSenseSystemInformation = newValue);
        listeners.init(KeyTools.createKey(FlightControllerKey.KeyFailsafeAction), (oldValue, newValue) -> failSafeAction = newValue);
        listeners.init(KeyTools.createKey(FlightAssistantKey.KeyLandingProtectionEnabled), (oldValue, newValue) -> landingProtectionEnabled = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightAssistantKey.KeyPrecisionLandingEnabled), (oldValue, newValue) -> precisionLandingEnabled = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightAssistantKey.KeyRTHObstacleAvoidanceEnabled), (oldValue, newValue) -> returnHomeObstacleAvoidanceEnabled = newValue != null && newValue);
        listeners.init(KeyTools.createKey(FlightAssistantKey.KeyVisionPositioningEnabled), (oldValue, newValue) -> visionPositioningEnabled = newValue != null && newValue);
        listeners.init(KeyTools.createKey(AirLinkKey.KeyUpLinkQuality), (oldValue, newValue) -> uplinkQuality = newValue);
        listeners.init(KeyTools.createKey(AirLinkKey.KeyDownLinkQuality), (oldValue, newValue) -> downlinkQuality = newValue);
        listeners.init(KeyTools.createKey(AirLinkKey.KeyChannelNumber), (oldValue, newValue) -> ocuSyncChannel = newValue);
        listeners.init(KeyTools.createKey(AirLinkKey.KeyChannelSelectionMode), (oldValue, newValue) -> ocuSyncChannelSelectionMode = newValue);
        listeners.init(KeyTools.createKey(AirLinkKey.KeyFrequencyBand), (oldValue, newValue) -> ocuSyncFrequencyBand = newValue);
        listeners.init(KeyTools.createKey(FlightAssistantKey.KeyBottomAuxiliaryLightMode), (oldValue, newValue) -> auxiliaryLightModeBottom = newValue);

        PerceptionManager.getInstance().addPerceptionInformationListener(this);
        PerceptionManager.getInstance().addObstacleDataListener(this);
        WaypointMissionManager.getInstance().addWaypointMissionExecuteStateListener(this);
    }

    public void close() {
        listeners.cancelAll();
        PerceptionManager.getInstance().removePerceptionInformationListener(this);
        PerceptionManager.getInstance().removeObstacleDataListener(this);
        WaypointMissionManager.getInstance().removeWaypointMissionExecuteStateListener(this);
    }

    public DatedValue<DroneStateAdapter> asDatedValue() {
        return new DatedValue<>(this, isFlying ? updated : new Date());
    }

    @Override
    public List<Message> getStatusMessages() {
        final List<Message> messages = new ArrayList<>();

        if (flightMode == null) {
            messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_telemetry_unavailable), Message.Level.DANGER));
            return messages;
        }

        final Message fcGoHomeStateMessage = DronelinkAnzu.getMessage(context, fcGoHomeState);
        if (fcGoHomeStateMessage != null) {
            messages.add(fcGoHomeStateMessage);
        }
        else {
            final Message goHomeStatusMessage = DronelinkAnzu.getMessage(context, goHomeState);
            if (goHomeStatusMessage != null) {
                messages.add(goHomeStatusMessage);
            }
            else {
                if (isSeriousLowBatteryWarning) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_isSeriousLowBatteryWarning_title), Message.Level.WARNING));
                } else if (isLowBatteryWarning) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_isLowBatteryWarning_title), Message.Level.WARNING));
                }

                if (isOutOfDistanceLimit) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_isOutOfDistanceLimit_title), Message.Level.WARNING));
                } else if (isNearDistanceLimit) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_isNearDistanceLimit_title), Message.Level.WARNING));
                }

                if (isNearHeightLimit) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_isNearHeightLimit_title), Message.Level.WARNING));
                }

                if (DronelinkAnzu.isWaypointMissionState(waypointMissionExecuteState, new WaypointMissionExecuteState[]{WaypointMissionExecuteState.UPLOADING})) {
                    final Message message = DronelinkAnzu.getMessage(context, waypointMissionExecuteState);
                    if (message != null) {
                        messages.add(message);
                    }
                }

                final Message flightModeMessage = DronelinkAnzu.getMessage(context, flightMode, waypointMissionExecuteState);
                if (flightModeMessage != null) {
                    messages.add(flightModeMessage);
                }

                if (getLocation() == null) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_locationUnavailable_title), context.getString(R.string.AnzuDroneStateAdapter_statusMessages_locationUnavailable_details), Message.Level.DANGER));
                }

                if (!isHomeLocationSet) {
                    messages.add(new Message(context.getString(R.string.AnzuDroneStateAdapter_statusMessages_homeLocationNotSet_title), Message.Level.DANGER));
                }
            }
        }

        final Message windWarningMessage = DronelinkAnzu.getMessage(context, windWarning);
        if (windWarningMessage != null) {
            messages.add(windWarningMessage);
        }

        final List<CompassState> compassStates = this.compassStates;
        if (compassStates != null) {
            for (final CompassState state : compassStates) {
                final Message message = DronelinkAnzu.getMessage(context, state.getCompassSensorState());
                if (message != null) {
                    messages.add(message);
                }
            }
        }

        final List<Message> airSenseSystemInformationMessages = DronelinkAnzu.getMessages(context, airSenseSystemInformation);
        if (airSenseSystemInformationMessages != null) {
            messages.addAll(airSenseSystemInformationMessages);
        }

        final Message deviceStatusMessage = DronelinkAnzu.getMessage(DeviceStatusManager.getInstance().getCurrentDJIDeviceStatus());
        if (deviceStatusMessage != null) {
            messages.add(deviceStatusMessage);
        }

        final List<DJIDeviceHealthInfo> deviceHealthInfos = DeviceHealthManager.getInstance().getCurrentDJIDeviceHealthInfos();
        if (deviceHealthInfos != null && !deviceHealthInfos.isEmpty()) {
            //create a copy since DJI seems to modify this list while we are iterating
            final List<Message> healthStatusMessages = DronelinkAnzu.getMessages(new ArrayList<>(deviceHealthInfos));
            if (healthStatusMessages != null) {
                messages.addAll(healthStatusMessages);
            }
        }

        messages.addAll(drone.getStatusMessages());

        return messages;
    }

    @Override
    public String getMode() {
        return flightMode != null ? DronelinkAnzu.getString(context, flightMode) : flightModeString;
    }

    @Override
    public boolean isFlying() {
        return isFlying;
    }

    @Override
    public boolean isReturningHome() {
        return flightMode == FlightMode.GO_HOME;
    }

    @Override
    public boolean isLanding() {
        final FlightMode flightMode = this.flightMode;
        if (flightMode == null) {
            return false;
        }

        switch (flightMode) {
            case AUTO_LANDING:
            case FORCE_LANDING:
            case ATTI_LANDING:
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean isCompassCalibrating() {
        return isCompassCalibrating;
    }

    @Override
    public Message getCompassCalibrationMessage() {
        return DronelinkAnzu.getMessage(context, compassCalibrationState);
    }

    @Override
    public Location getLocation() {
        return DronelinkAnzu.getLocation(coordinate);
    }

    @Override
    public Location getHomeLocation() {
        return isHomeLocationSet ? DronelinkAnzu.getLocation(homeCoordinate) : null;
    }

    @Override
    public Location getLastKnownGroundLocation() {
        return DronelinkAnzu.getLocation(lastKnownGroundCoordinate);
    }

    @Override
    public Location getTakeoffLocation() {
        if (isFlying) {
            if (lastKnownGroundCoordinate != null) {
                return getLastKnownGroundLocation();
            }

            if (isHomeLocationSet) {
                return getHomeLocation();
            }
        }

        return getLocation();
    }

    @Override
    public Double getTakeoffAltitude() {
        return null;
    }

    @Override
    public double getCourse() {
        return Math.atan2(velocity.getY(), velocity.getX());
    }

    @Override
    public double getHorizontalSpeed() {
        return Math.sqrt(Math.pow(velocity.getX(), 2) + Math.pow(velocity.getY(), 2));
    }

    @Override
    public double getVerticalSpeed() {
        return velocity.getZ() == 0 ? 0 : -velocity.getZ();
    }

    @Override
    public double getAltitude() {
        return altitude;
    }

    @Override
    public Double getUltrasonicAltitude() {
        final Integer ultrasonicAltitude = this.ultrasonicAltitude;
        if (ultrasonicAltitude != null) {
            return ultrasonicAltitude.doubleValue();
        }
        return null;
    }

    @Override
    public Double getReturnHomeAltitude() {
        final Integer returnHomeAltitude = this.returnHomeAltitude;
        if (returnHomeAltitude != null) {
            return returnHomeAltitude.doubleValue();
        }
        return null;
    }

    @Override
    public Double getMaxAltitude() {
        final Integer maxAltitude = this.maxAltitude;
        if (maxAltitude != null) {
            return maxAltitude.doubleValue();
        }
        return null;
    }

    @Override
    public Double getBatteryPercent() {
        final Integer batterPercent = this.batterPercent;
        if (batterPercent != null) {
            return batterPercent.doubleValue() / 100.0;
        }
        return null;
    }

    @Override
    public Double getLowBatteryThreshold() {
        final Integer lowBatteryThreshold = this.lowBatteryThreshold;
        if (lowBatteryThreshold != null) {
            return lowBatteryThreshold.doubleValue() / 100.0;
        }
        return null;
    }

    @Override
    public Double getFlightTimeRemaining() {
        final Integer flightTimeRemaining = this.flightTimeRemaining;
        if (flightTimeRemaining != null) {
            return flightTimeRemaining.doubleValue();
        }
        return null;
    }

    @Override
    public DroneObstacleAvoidanceSpecification getObstacleAvoidanceSpecification() {
        return obstacleAvoidanceSpecification;
    }

    @Override
    public Double getObstacleDistance() {
        final ObstacleData obstacleData = this.obstacleData;
        if (obstacleData == null) {
            return null;
        }

        int nearestDistance = Math.min(obstacleData.getUpwardObstacleDistance(), obstacleData.getDownwardObstacleDistance());
        for (final int horizontalDistance : obstacleData.getHorizontalObstacleDistance()) {
            nearestDistance = Math.min(nearestDistance, horizontalDistance);
        }

        return (double)nearestDistance;
    }

    @Override
    public Orientation3 getOrientation() {
        final Orientation3 orientation = new Orientation3();
        final Attitude attitude = this.attitude;
        if (attitude != null) {
            orientation.x = Convert.DegreesToRadians(attitude.getPitch());
            orientation.y = Convert.DegreesToRadians(attitude.getRoll());
            orientation.z = Convert.DegreesToRadians(attitude.getYaw());
        }
        return orientation;
    }

    @Override
    public Integer getGPSSatellites() {
        return gpsSatellites;
    }

    @Override
    public Double getGPSSignalStrength() {
        return DronelinkAnzu.getGPSSignalStrength(gpsSignalLevel);
    }

    @Override
    public Double getUplinkSignalStrength() {
        final Integer uplinkQuality = this.uplinkQuality;
        if (uplinkQuality != null) {
            return uplinkQuality.doubleValue() / 100.0;
        }
        return null;
    }

    @Override
    public Double getDownlinkSignalStrength() {
        final Integer downlinkQuality = this.downlinkQuality;
        if (downlinkQuality != null) {
            return downlinkQuality.doubleValue() / 100.0;
        }
        return null;
    }

    @Override
    public DroneLightbridgeFrequencyBand getLightbridgeFrequencyBand() {
        return DroneLightbridgeFrequencyBand.UNKNOWN;
    }

    @Override
    public DroneOcuSyncFrequencyBand getOcuSyncFrequencyBand() {
        return DronelinkAnzu.getOcuSyncFrequencyBand(ocuSyncFrequencyBand);
    }

    @Override
    public DroneAuxiliaryLightMode getAuxiliaryLightModeBottom() {
        return DronelinkAnzu.getDroneAuxiliaryLightMode(auxiliaryLightModeBottom);
    }

    @Override
    public void onUpdate(final ObstacleData obstacleData) {
        this.obstacleData = obstacleData;
    }

    @Override
    public void onMissionStateUpdate(final WaypointMissionExecuteState missionState) {
        this.waypointMissionExecuteState = missionState;
    }

    @Override
    public void onUpdate(@NonNull PerceptionInfo information) {
        obstacleAvoidanceSpecification = new DroneObstacleAvoidanceSpecification(
                DronelinkAnzu.getDroneObstacleAvoidanceMode(information.getObstacleAvoidanceType()),
                new HashMap<DroneObstacleAvoidanceDirection, Boolean>() {{
                    put(DroneObstacleAvoidanceDirection.HORIZONTAL, information.isHorizontalObstacleAvoidanceEnabled());
                    put(DroneObstacleAvoidanceDirection.UPWARD, information.isUpwardObstacleAvoidanceEnabled());
                    put(DroneObstacleAvoidanceDirection.DOWNWARD, information.isDownwardObstacleAvoidanceEnabled());
                }},
                new HashMap<DroneObstacleAvoidanceDirection, Double>() {{
                    put(DroneObstacleAvoidanceDirection.HORIZONTAL, information.getHorizontalObstacleAvoidanceBrakingDistance());
                    put(DroneObstacleAvoidanceDirection.UPWARD, information.getUpwardObstacleAvoidanceBrakingDistance());
                    put(DroneObstacleAvoidanceDirection.DOWNWARD, information.getDownwardObstacleAvoidanceBrakingDistance());
                }},
                new HashMap<DroneObstacleAvoidanceDirection, Double>() {{
                    put(DroneObstacleAvoidanceDirection.HORIZONTAL, information.getHorizontalObstacleAvoidanceWarningDistance());
                    put(DroneObstacleAvoidanceDirection.UPWARD, information.getUpwardObstacleAvoidanceWarningDistance());
                    put(DroneObstacleAvoidanceDirection.DOWNWARD, information.getDownwardObstacleAvoidanceWarningDistance());
                }},
                returnHomeObstacleAvoidanceEnabled
        );
    }
}
