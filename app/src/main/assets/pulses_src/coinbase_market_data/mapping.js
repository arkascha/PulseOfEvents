// Coinbase Market Data - Minimal UI Mapping

// Initialize persistent state if it doesn't exist
if (typeof productAverages === 'undefined') {
    var productAverages = {}; // Format: { "BTC-USD": { sum: 0, count: 0 }, ... }
}

var result = {
    sample: "dialog-information", 
    pitch: 1.0,
    volume: 0.5
};

if (event.type === "ticker") {
    var id = event.product_id;
    var price = parseFloat(event.price);
    
    // Initialize state for new products
    if (!productAverages[id]) {
        productAverages[id] = { sum: 0, count: 0 };
    }
    
    // Update running average
    var stats = productAverages[id];
    if (stats.count < 100) {
        stats.sum += price;
        stats.count += 1;
    } else {
        stats.sum -= stats.sum / stats.count;
        stats.sum += price;
    }
    var average = stats.sum / stats.count;
    
    var priceRatio = price / average;
    var sensitivity = 250;
    var modulatedPitch = 1.0 + (priceRatio - 1.0) * sensitivity;
    
    result.pitch = Math.max(0.5, Math.min(modulatedPitch, 2.0)).toFixed(2);
    result.volume = 0.4 + Math.min(parseFloat(event.last_size) * 10, 0.6);
    
    // Map side to UI sounds
    if (event.side === "buy") {
        if (id === "BTC-USD") {
            result.sample = "completion-success";
        } else {
            result.sample = "dialog-information";
        }
    } else {
        result.sample = "dialog-warning";
    }
}

result;
