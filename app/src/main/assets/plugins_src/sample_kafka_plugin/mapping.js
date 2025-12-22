// PulseOfEvents Mapping Script
// Example: E-Commerce Orders

var result = {
    sample: "perc-808", // Default fallback
    pitch: 1.0,
    volume: 0.5
};

// Access properties of the 'event' object
// Note: Rhino allows direct property access or 'event.get("type")' depending on JSON parsing
if (event.type === "ORDER_CREATED") {
    result.sample = "kick-808";
    // Scale volume by order amount (assuming 0-500 range)
    result.volume = Math.min(event.amount / 500, 1.0);
} 
else if (event.type === "SHIPMENT_SENT") {
    result.sample = "snare-808";
    result.volume = 0.8;
}
else if (event.type === "PAYMENT_FAILED") {
    result.sample = "perc-nasty";
    result.volume = 1.0;
    result.pitch = 0.5;
}

// Return the result object
result;
