// Rhythmic Chaos Mapping

var samples = [
    "Kick - Tekk", "Snare - OG", "Perc - Box", "Hats - Metal", "Clap - Tape", "Kick - Juno", "Snare - VHS"
];

// We pick a random sample for the WHOLE BURST. 
// Note: Since this script is evaluated for every note, 
// we use a simple hash of the current second to stay on the same sample for a while.
var timeBucket = Math.floor(Date.now() / 2000); 
var randomIdx = timeBucket % samples.length;

var result = {
    sample: samples[randomIdx],
    pitch: 1.0, // Base pitch (will be modulated by the RhythmicChaosService)
    volume: 0.8
};

result;
