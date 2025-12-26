// Rhythmic Chaos Mapping (modern-minimalistic-ui)

var samples = [
    "message-new-instant", "dialog-information", "completion-success", "audio-volume-change", "camera-shutter"
];

// We pick a random sample for the WHOLE BURST. 
var timeBucket = Math.floor(Date.now() / 2000); 
var randomIdx = timeBucket % samples.length;

var result = {
    sample: samples[randomIdx],
    pitch: 1.0, 
    volume: 0.8
};

result;
