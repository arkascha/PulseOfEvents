// Variable Timed Simulator Mapping Script (Oxygen)

var result = {
    sample: "message-new-instant", 
    pitch: 1.0,
    volume: 0.5
};

// Access properties of the 'event' object
if (event.type === "SUCCESS") {
    result.sample = "complete";
    result.volume = 0.7;
    result.pitch = 0.9 + (Math.random() * 0.2);
} 
else if (event.type === "FAILURE") {
    result.sample = "dialog-error";
    result.volume = 0.8;
    result.pitch = 0.8 + (Math.random() * 0.4);
}
else if (event.type === "TICK") {
    result.sample = "message-new-instant"; 
    result.volume = 0.3;
    result.pitch = 0.5 + (Math.random() * 0.1);
}

result;
