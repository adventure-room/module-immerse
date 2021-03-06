package com.programyourhome.adventureroom.module.immerse.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import org.la4j.Vectors;

import com.programyourhome.immerse.domain.Factory;
import com.programyourhome.immerse.domain.ImmerseSettings;
import com.programyourhome.immerse.domain.Scenario;
import com.programyourhome.immerse.domain.ScenarioSettings;
import com.programyourhome.immerse.domain.audio.resource.AudioFileType;
import com.programyourhome.immerse.domain.format.ImmerseAudioFormat;
import com.programyourhome.immerse.domain.location.Vector3D;
import com.programyourhome.immerse.domain.location.dynamic.DynamicLocation;
import com.programyourhome.immerse.domain.speakers.Speaker;
import com.programyourhome.immerse.domain.speakers.SpeakerVolumeRatios;
import com.programyourhome.immerse.toolbox.audio.playback.ForeverPlayback;
import com.programyourhome.immerse.toolbox.audio.playback.LoopPlayback;
import com.programyourhome.immerse.toolbox.audio.playback.TimerPlayback;
import com.programyourhome.immerse.toolbox.audio.resource.FileAudioResource;
import com.programyourhome.immerse.toolbox.audio.resource.UrlAudioResource;
import com.programyourhome.immerse.toolbox.location.dynamic.FixedDynamicLocation;
import com.programyourhome.immerse.toolbox.location.dynamic.HorizontalCircleDynamicLocation;
import com.programyourhome.immerse.toolbox.location.dynamic.KeyFramesDynamicLocation;
import com.programyourhome.immerse.toolbox.speakers.algorithms.normalize.FractionalNormalizeAlgorithm;
import com.programyourhome.immerse.toolbox.speakers.algorithms.normalize.MaxSumNormalizeAlgorithm;
import com.programyourhome.immerse.toolbox.speakers.algorithms.volumeratios.FieldOfHearingVolumeRatiosAlgorithm;
import com.programyourhome.immerse.toolbox.speakers.algorithms.volumeratios.FixedVolumeRatiosAlgorithm;
import com.programyourhome.immerse.toolbox.volume.dynamic.FixedDynamicVolume;
import com.programyourhome.immerse.toolbox.volume.dynamic.LinearDynamicVolume;

import one.util.streamex.StreamEx;

public class ScenarioBuilderImpl implements ScenarioBuilder {

    private final ImmerseSettings immerseSettings;
    private final Scenario.Builder immerseScenarioBuilder;
    private final ScenarioSettings.Builder immerseScenarioSettingsBuilder;

    public ScenarioBuilderImpl(ImmerseSettings immerseSettings) {
        this.immerseSettings = immerseSettings;
        this.immerseScenarioBuilder = Scenario.builder()
                .name("Builder Scenario")
                .description("Scenario built by the ScenarioBuilder");
        this.immerseScenarioSettingsBuilder = ScenarioSettings.builder();
        this.fullVolume();
        this.sourceAtAllSpeakers();
        this.normalizeVolume();
        this.playOnce();
    }

    @Override
    public ScenarioBuilder name(String name) {
        this.immerseScenarioBuilder.name(name);
        return this;
    }

    @Override
    public ScenarioBuilder description(String name) {
        this.immerseScenarioBuilder.description(name);
        return this;
    }

    @Override
    public ScenarioBuilder file(String path) {
        this.immerseScenarioSettingsBuilder.audioResource(FileAudioResource.file(path));
        return this;
    }

    @Override
    public ScenarioBuilder urlWithFormat(String url, ImmerseAudioFormat format) {
        this.immerseScenarioSettingsBuilder.audioResource(UrlAudioResource.urlWithFormat(url, format));
        return this;
    }

    @Override
    public ScenarioBuilder urlWithType(String url, AudioFileType type) {
        this.immerseScenarioSettingsBuilder.audioResource(UrlAudioResource.urlWithType(url, type));
        return this;
    }

    @Override
    public ScenarioBuilder volume(double volume) {
        this.immerseScenarioSettingsBuilder.volume(FixedDynamicVolume.fixed(volume));
        return this;
    }

    @Override
    public ScenarioBuilder fullVolume() {
        this.immerseScenarioSettingsBuilder.volume(FixedDynamicVolume.full());
        return this;
    }

    @Override
    public ScenarioBuilder muteVolume() {
        this.immerseScenarioSettingsBuilder.volume(FixedDynamicVolume.mute());
        return this;
    }

    @Override
    public ScenarioBuilder linearVolume(double from, double to, long inMillis) {
        this.immerseScenarioSettingsBuilder.volume(LinearDynamicVolume.linear(from, to, inMillis, true));
        return this;
    }

    @Override
    public ScenarioBuilder linearVolumeWithDelay(double from, double to, long inMillis, long delayMillis) {
        this.immerseScenarioSettingsBuilder.volume(LinearDynamicVolume.linearWithDelay(from, to, inMillis, true, delayMillis));
        return this;
    }

    @Override
    public ScenarioBuilder sourceAtSpeaker(int speakerId) {
        return this.sourceAtSpeakers(Arrays.asList(speakerId));
    }

    @Override
    public ScenarioBuilder sourceAtSpeakers(Collection<Integer> speakerIds) {
        return this.fixedVolumesRelative(this.volumeAtSpeakers(speakerIds));
    }

    @Override
    public ScenarioBuilder sourceAtAllSpeakers() {
        return this.sourceAtSpeakers(this.immerseSettings.getRoom().getSpeakers().keySet());
    }

    @Override
    public Factory<DynamicLocation> atLocation(Vector3D location) {
        return FixedDynamicLocation.fixed(location);
    }

    // TODO: unit test this!
    @Override
    public Factory<DynamicLocation> atPath(List<Vector3D> path, double unitsPerSecond, boolean loop) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path cannot be empty");
        }
        SortedMap<Long, Vector3D> keyFrames = new TreeMap<>();
        double travelTimeSoFar = 0;
        keyFrames.put(Math.round(travelTimeSoFar), path.get(0));
        for (int i = 1; i < path.size(); i++) {
            Vector3D previousPoint = path.get(i - 1);
            Vector3D nextPoint = path.get(i);
            double distance = previousPoint.toLa4j().subtract(nextPoint.toLa4j()).fold(Vectors.mkEuclideanNormAccumulator());
            double travelTimeInMillis = distance / unitsPerSecond * 1000;
            travelTimeSoFar += travelTimeInMillis;
            keyFrames.put(Math.round(travelTimeSoFar), nextPoint);
        }
        return KeyFramesDynamicLocation.keyFrames(keyFrames, loop);
    }

    // TODO: unit test
    @Override
    public Factory<DynamicLocation> circling(Vector3D center, double startAngle, double radius, double unitsPerSecond, boolean clockwise) {
        double circumference = 2 * Math.PI * radius;
        double millisPerFullCircle = circumference / unitsPerSecond * 1000;
        return HorizontalCircleDynamicLocation.horizontalCircle(center, startAngle, radius, clockwise, millisPerFullCircle);
    }

    @Override
    public Factory<DynamicLocation> atCenter() {
        return this.calculateCenterOfRoom();
    }

    @Override
    public ScenarioBuilder fieldOfHearingVolume(Factory<DynamicLocation> sourceLocation, Factory<DynamicLocation> listenerLocation) {
        this.immerseScenarioSettingsBuilder.volumeRatiosAlgorithm(
                FieldOfHearingVolumeRatiosAlgorithm.fieldOfHearing(this.immerseSettings.getRoom(), sourceLocation, listenerLocation));
        return this;
    }

    @Override
    public ScenarioBuilder fieldOfHearingVolume(Factory<DynamicLocation> sourceLocation, Factory<DynamicLocation> listenerLocation, double angle) {
        this.immerseScenarioSettingsBuilder.volumeRatiosAlgorithm(
                FieldOfHearingVolumeRatiosAlgorithm.fieldOfHearing(this.immerseSettings.getRoom(), sourceLocation, listenerLocation, angle));
        return this;
    }

    @Override
    public ScenarioBuilder fixedVolumesRelative(Map<Integer, Double> relativeSpeakerVolumes) {
        this.immerseScenarioSettingsBuilder.volumeRatiosAlgorithm(FixedVolumeRatiosAlgorithm.fixed(new SpeakerVolumeRatios(relativeSpeakerVolumes)));
        return this;
    }

    @Override
    public ScenarioBuilder normalizeVolume() {
        this.immerseScenarioSettingsBuilder.normalizeAlgorithm(FractionalNormalizeAlgorithm.fractional());
        return this;
    }

    @Override
    public ScenarioBuilder volumeAsOneSpeaker() {
        this.immerseScenarioSettingsBuilder.normalizeAlgorithm(MaxSumNormalizeAlgorithm.maxSum(1));
        return this;
    }

    @Override
    public ScenarioBuilder maxSumVolume(double maxSum) {
        this.immerseScenarioSettingsBuilder.normalizeAlgorithm(MaxSumNormalizeAlgorithm.maxSum(maxSum));
        return this;
    }

    @Override
    public ScenarioBuilder playOnce() {
        this.immerseScenarioSettingsBuilder.playback(LoopPlayback.once());
        return this;
    }

    @Override
    public ScenarioBuilder playRepeat(int times) {
        this.immerseScenarioSettingsBuilder.playback(LoopPlayback.times(times));
        return this;
    }

    @Override
    public ScenarioBuilder playRepeatForever() {
        this.immerseScenarioSettingsBuilder.playback(ForeverPlayback.forever());
        return this;
    }

    @Override
    public ScenarioBuilder playForDuration(Duration duration) {
        this.immerseScenarioSettingsBuilder.playback(TimerPlayback.timer(duration.toMillis()));
        return this;
    }

    @Override
    public Scenario build() {
        return this.immerseScenarioBuilder.settings(this.immerseScenarioSettingsBuilder.build()).build();
    }

    private Factory<DynamicLocation> calculateCenterOfRoom() {
        return FixedDynamicLocation.fixed(
                this.calculateCenterOfAxis(Vector3D::getX),
                this.calculateCenterOfAxis(Vector3D::getY),
                this.calculateCenterOfAxis(Vector3D::getZ));
    }

    private double calculateCenterOfAxis(ToDoubleFunction<Vector3D> axisValueFunction) {
        Collection<Speaker> speakers = this.immerseSettings.getRoom().getSpeakers().values();
        return StreamEx.of(speakers)
                .map(Speaker::getPosition)
                .mapToDouble(axisValueFunction)
                .sum() / speakers.size();
    }

    private Map<Integer, Double> volumeAtSpeakers(Collection<Integer> speakerIds) {
        return StreamEx.of(this.immerseSettings.getRoom().getSpeakers().keySet())
                .toMap(speakerId -> speakerId, speakerId -> speakerIds.contains(speakerId) ? 1.0 : 0.0);
    }

}
