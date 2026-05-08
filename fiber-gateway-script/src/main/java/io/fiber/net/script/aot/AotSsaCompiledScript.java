package io.fiber.net.script.aot;

import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.script.Script;
import io.fiber.net.script.parse.Compiled;
import io.fiber.net.script.parse.OptimiserNodeVisitor;
import io.fiber.net.script.parse.ParseException;
import io.fiber.net.script.run.AbstractVm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class AotSsaCompiledScript implements Script {
    private static final MethodType CONSTRUCTOR_TYPE = MethodType.methodType(void.class, JsonNode.class, Object.class,
            Maybe.Emitter.class);

    public static AotSsaCompiledScript of(Compiled compiled) throws ParseException {
        SsaVmClassGenerator directGenerator = new SsaVmClassGenerator(compiled);
        try {
            Class<? extends AbstractVm> aotClass = directGenerator.loadAsClass();
            MethodHandle handle = MethodHandles.lookup().findConstructor(aotClass, CONSTRUCTOR_TYPE);
            return new AotSsaCompiledScript(handle, compiled.containsAsyncIS());
        } catch (Throwable e) {
            throw new ParseException("could not generate ssa aot class", e);
        }
    }

    private final MethodHandle constructor;
    private final boolean containsAsync;

    private AotSsaCompiledScript(MethodHandle constructor, boolean containsAsync) {
        this.constructor = constructor;
        this.containsAsync = containsAsync;
    }

    @Override
    public Maybe<JsonNode> exec(JsonNode root, Object attach) {
        return Maybe.create(emitter -> createVm(root, attach, emitter).exec());
    }

    @Override
    public boolean containsAsyncIR() {
        return containsAsync;
    }

    @Override
    public JsonNode execForSync(JsonNode root, Object attach) throws Throwable {
        if (containsAsyncIR()) {
            throw new IllegalStateException("cannot sync exec for async script");
        }
        AbstractVm vm = createVm(root, attach, OptimiserNodeVisitor.noopEmitter());
        vm.exec();
        return vm.getResultNow();
    }

    private AbstractVm createVm(JsonNode root, Object attach, Maybe.Emitter<JsonNode> resultEmitter) throws Throwable {
        return (AbstractVm) constructor.invoke(root, attach, resultEmitter);
    }
}
