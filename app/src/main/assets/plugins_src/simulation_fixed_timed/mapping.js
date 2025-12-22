// Heavy Simulator Mapping Script - 99Sounds I Logic

var result = {
    sample: "kick-808", 
    pitch: 1.0,
    volume: 0.5
};

// Simple logic based on event type
switch(event.type) {
    case "ORDER":
        result.sample = "kick-808";
        result.volume = Math.min(event.val / 100, 1.0);
        result.pitch = 0.8 + (event.count * 0.05);
        break;
    case "PAYMENT":
        result.sample = "cowbell-808";
        result.volume = 0.8;
        result.pitch = 1.2;
        break;
    case "SHIPPING":
        result.sample = "snare-808";
        result.volume = 0.6;
        break;
    case "CHAT":
        result.sample = "hihat-808";
        result.volume = 0.3;
        break;
    case "ADMIN":
        result.sample = "clap-808";
        result.volume = 0.7;
        break;
    default:
        result.sample = "tom-808";
        result.volume = 0.4;
}

result;
