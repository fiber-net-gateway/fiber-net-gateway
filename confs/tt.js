
directive l = http "http://localhost:15004";

let data = l.request({
    path: "/caas-activecore-custom-app/user/10000"
});
sleep(10015);

let data2;
try {
    data2 = l.request({
        path: "/caas-activecore-custom-app/user/10000"
    });
} catch (e) {
    return {data, e};
}


return {data, data2};