// Violin Timed Simulator Mapping Script

var result = {
    sample: "violin-win-1", 
    pitch: 1.0,
    volume: 0.5
};

// Access properties of the 'event' object
if (event.type === "SUCCESS") {
    var index = Math.floor(Math.random() * 5) + 1;
    result.sample = "violin-win-" + index;
    result.volume = 0.7;
    result.pitch = 0.9 + (Math.random() * 0.2);
} 
else if (event.type === "FAILURE") {
    var index = Math.floor(Math.random() * 5) + 1;
    result.sample = "violin-lose-" + index;
    result.volume = 0.8;
    result.pitch = 0.8 + (Math.random() * 0.4);
}
else if (event.type === "TICK") {
    result.sample = "violin-win-5"; // Use a specific sound for ticks
    result.volume = 0.3;
    result.pitch = 0.5 + (Math.random() * 0.1);
}

result;
