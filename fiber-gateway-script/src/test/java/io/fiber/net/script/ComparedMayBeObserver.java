package io.fiber.net.script;

import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Maybe;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.json.JsonNode;

import java.util.Objects;

public class ComparedMayBeObserver {
    private static final int S_INIT = 0;
    private static final int S_SEC = 1;
    private static final int S_CPT = 2;
    private static final int S_ERR = 3;

    private Ob ob1, ob2;
    private final String name;

    public ComparedMayBeObserver(String name) {
        this.name = name;
    }

    public Ob getOb() {
        if (ob1 != null) {
            return ob2 = new Ob(this);
        }
        return ob1 = new Ob(this);
    }

    private void eq() {
        if (ob2 == null || ob1.state == S_INIT || ob2.state == S_INIT) {
            return;
        }

        if (ob1.equals(ob2)) {
            System.out.println(name + " 成功：" + ob1);
        } else {
            System.err.println(name + "失败：");
            System.err.println(ob1);
            System.err.println("================================================");
            System.err.println(ob2);
        }
    }


    public static class Ob implements Maybe.Observer<JsonNode> {
        private int state = S_INIT;
        private JsonNode jsonNode;
        private final ComparedMayBeObserver outer;

        public Ob(ComparedMayBeObserver outer) {
            this.outer = outer;
        }

        @Override
        public void onSubscribe(Disposable d) {
        }

        @Override
        public void onSuccess(JsonNode node) {
            state = S_SEC;
            jsonNode = node;
            outer.eq();
        }

        @Override
        public void onError(Throwable e) {
            state = S_ERR;
            e.printStackTrace(System.err);
            outer.eq();

        }

        @Override
        public void onComplete() {
            state = S_CPT;
            outer.eq();

        }

        @Override
        public Scheduler scheduler() {
            return Scheduler.current();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Ob ob = (Ob) object;
            return state == ob.state && Objects.equals(jsonNode, ob.jsonNode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, jsonNode);
        }

        @Override
        public String toString() {
            return "Ob{" +
                    "state=" + state +
                    ", jsonNode=" + jsonNode +
                    '}';
        }
    }


}
