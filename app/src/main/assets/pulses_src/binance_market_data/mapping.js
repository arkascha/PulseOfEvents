// Binance Combined Trade Stream Mapping - Snares (ds-cobald)
// Handling both BTCUSDT and ETHUSDT

// Initialize persistent state for price averages
if (typeof priceStats === 'undefined') {
    var priceStats = {}; 
}

var data = event.data || event;
var symbol = data.s || "UNKNOWN";

var result = {
    sample: "message-new-instant",
    pitch: 1.0,
    volume: 0.3
};

// 1. Process Price for Pitch (Moving Average)
var currentPrice = parseFloat(data.p);
if (!priceStats[symbol]) {
    priceStats[symbol] = { sum: 0, count: 0 };
}
var stats = priceStats[symbol];
if (stats.count < 100) {
    stats.sum += currentPrice;
    stats.count += 1;
} else {
    stats.sum -= stats.sum / stats.count;
    stats.sum += currentPrice;
}
var average = stats.sum / stats.count;

var priceRatio = currentPrice / average;
var sensitivity = 200; 
var modulatedPitch = 1.0 + (priceRatio - 1.0) * sensitivity;

// 2. Process Quantity for Volume
var quantity = parseFloat(data.q);
var divisor = symbol.indexOf("BTC") !== -1 ? 0.5 : 5.0; 
result.volume = Math.min(0.3 + (quantity / divisor), 1.0);

// 3. Sample Selection based on pressure and currency
if (data.m === true) {
    // Sell-side pressure
    result.sample = "audio-volume-change";
    result.pitch = modulatedPitch * 0.9;
} else {
    // Buy-side pressure
    if (symbol.indexOf("BTC") !== -1) {
        result.sample = "outcome-success";
    } else {
        result.sample = "message-new-instant";
    }
    result.pitch = modulatedPitch;
}

// Final Clamp for SoundPool
result.pitch = Math.max(0.5, Math.min(result.pitch, 2.0));

result;
