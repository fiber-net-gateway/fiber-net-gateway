let result = {};
try {
    let token = req.readJson({
        query: {
            code: "OC-296-nA7zoOOPhCJYEajM9Cxiz0DtKlaoNc3Uv62QU0vwTxlCwYl4",
            client_secret: "c56f8414-687b-452c-8b32-d40496ab",
            client_id: "APP010",
            grant_type: "authorization_code"
        }


    });
    result = req.getHeader({param: {access_token: token}})
} catch (e) {
    result.err = e
};
return result;