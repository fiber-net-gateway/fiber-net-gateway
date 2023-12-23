directive demoService from dubbo "com.test.dubbo.DemoService";

req.discardBody();
let user = demoService.createUser("user_name");
resp.send(200, {dubboResult: user, state: 0});