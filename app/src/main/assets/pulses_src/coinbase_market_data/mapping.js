// Coinbase Market Data - Stateful Triumphant Orchestra Mapping

// Initialize persistent state if it doesn't exist
if (typeof productAverages === 'undefined') {
    var productAverages = {}; // Format: { "BTC-USD": { sum: 0, count: 0 }, ... }
}

var result = {
    sample: "11l-triumphant_orchestra-1749487524691-360361", 
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
    
    // Update running average (approximate moving average over last 100 events)
    var stats = productAverages[id];
    if (stats.count < 100) {
        stats.sum += price;
        stats.count += 1;
    } else {
        // Simple exponential moving average approximation
        stats.sum -= stats.sum / stats.count;
        stats.sum += price;
    }
    var average = stats.sum / stats.count;
    
    // Pitch Logic:
    // We want to hear the percentage difference from the average.
    // 1.0 means exactly on average. 
    var priceRatio = price / average;
    
    // We amplify the difference so that small market moves (e.g. 0.1%) 
    // result in noticeable pitch changes.
    // Sensitivity: how aggressive the pitch changes are.
    var sensitivity = 250;
    var modulatedPitch = 1.0 + (priceRatio - 1.0) * sensitivity;
    
    // Clamp between 0.5 and 2.0 for SoundPool compatibility
    result.pitch = Math.max(0.5, Math.min(modulatedPitch, 2.0)).toFixed(2);
    
    // Volume: Based on trade size, with a minimum base of 0.4
    result.volume = 0.4 + Math.min(parseFloat(event.last_size) * 10, 0.6);
    
    // Map side to triumphant sounds
    if (event.side === "buy") {
        if (id === "BTC-USD") {
            result.sample = "11l-triumphant_orchestra-1749487495427-360356";
        } else {
            result.sample = "11l-triumphant_orchestra-1749487502507-360359";
        }
    } else {
        result.sample = "11l-triumphant_orchestra-1749487505211-360357";
    }
}

result;
