directive demoService from dubbo "com.test.dubbo.DemoService";

req.discardBody();
let user = demoService.createUser("user_name");
if (user.age == 0) {
    user.age = 20;
    user.name = demoService.$dynamicInvoke("createUser", [user.name + "333"]).name;
}
let user2 = demoService.create({"name":"33333", age: user.age, male: !user.male});
return {dubboResult: user, state: 0,user2};