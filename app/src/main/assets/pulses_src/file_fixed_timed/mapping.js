// Heavy Simulator Mapping Script - MIUI Logic

var result = {
    sample: "message-new-instant", 
    pitch: 1.0,
    volume: 0.5
};

// Simple logic based on event type
switch(event.type) {
    case "ORDER":
        result.sample = "message-new-instant";
        result.volume = Math.min(event.val / 100, 1.0);
        result.pitch = 0.8 + (event.count * 0.05);
        break;
    case "PAYMENT":
        result.sample = "complete";
        result.volume = 0.8;
        result.pitch = 1.2;
        break;
    case "SHIPPING":
        result.sample = "audio-volume-change";
        result.volume = 0.6;
        break;
    case "CHAT":
        result.sample = "bell";
        result.volume = 0.3;
        break;
    case "ADMIN":
        result.sample = "dialog-information";
        result.volume = 0.7;
        break;
    default:
        result.sample = "system-ready";
        result.volume = 0.4;
}

result;
