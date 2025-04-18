
directive rl = rate_limiter "2/3s";
req.discardBody();

if (req.getMethod() == "GET") {
    if(rl.acquire()) {
        return "OK";
    } else {
        throw "BLOCKED"
    }
} else {
    if (rl.mustAcquire(3000)) {// mustAcquire will throw exception. if failed
        return "OK";
    } else {
        // not hit
        throw "BLOCKED"
    }
}

