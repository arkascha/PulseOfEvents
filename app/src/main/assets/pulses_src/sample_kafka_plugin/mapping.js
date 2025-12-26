// PulseOfEvents Mapping Script (ds-cobald)
// Example: E-Commerce Orders

var result = {
    sample: "message-new-instant", // Default fallback
    pitch: 1.0,
    volume: 0.5
};

// Access properties of the 'event' object
if (event.type === "ORDER_CREATED") {
    result.sample = "outcome-success";
    // Scale volume by order amount (assuming 0-500 range)
    result.volume = Math.min(event.amount / 500, 1.0);
} 
else if (event.type === "SHIPMENT_SENT") {
    result.sample = "complete-copy";
    result.volume = 0.8;
}
else if (event.type === "PAYMENT_FAILED") {
    result.sample = "dialog-error";
    result.volume = 1.0;
    result.pitch = 0.5;
}

// Return the result object
result;
