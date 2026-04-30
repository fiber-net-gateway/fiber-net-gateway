let result = {};
try {
    let token = req.readJson();
    result = req.getHeader({param: {access_token: token}})
} catch (e) {
    result.err = e
};
return result;
