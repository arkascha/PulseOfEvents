// Random Chaos Generator Mapping (Steam)

var samples = [
    "message", "bell", "audio-volume-change", "device-added", "device-removed", "screen-capture", "power-plug", "power-unplug", "battery-low"
];

// Pick a random sample from the array
var randomIdx = Math.floor(Math.random() * samples.length);

// Result determines sound playback
var result = {
    sample: samples[randomIdx],
    pitch: 0.5 + (Math.random() * 1.5),
    volume: 0.3 + (Math.random() * 0.7)
};

result;
