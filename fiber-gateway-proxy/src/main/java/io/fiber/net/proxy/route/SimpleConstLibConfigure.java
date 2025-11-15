package io.fiber.net.proxy.route;

import io.fiber.net.proxy.HttpLibConfigure;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Library;

import java.util.function.Consumer;

public class SimpleConstLibConfigure implements HttpLibConfigure {
    private final ConstPackage[] packages = new ConstPackage[VarType.LENGTH];

    @Override
    public void onInit(ExtensiveHttpLib lib) {

    }

    public VarConst findOrCreateVar(VarType type, String name) {
        ConstPackage cp = packages[type.ordinal()];
        if (cp == null) {
            cp = packages[type.ordinal()] = new ConstPackage(type);
        }
        return cp.getOrCreate(name);
    }

    public VarConst findVar(VarType type, String name) {
        ConstPackage cp = packages[type.ordinal()];
        if (cp == null) {
            return null;
        }
        return cp.get(name);
    }

    public ConstPackage get(VarType type) {
        return packages[type.ordinal()];
    }

    @Override
    public Library.Constant findConstant(String namespace, String key) {

        if ("$path".equals(namespace)) {
            return findOrCreateVar(VarType.PATH, key);
        }

        if ("$query".equals(namespace)) {
            return findOrCreateVar(VarType.QUERY, key);
        }

        if ("$header".equals(namespace)) {
            return findOrCreateVar(VarType.REQ_HEADER, key);
        }

        if ("$cookie".equals(namespace)) {
            return findOrCreateVar(VarType.COOKIE, key);
        }

        if ("$context".equals(namespace)) {
            return findOrCreateVar(VarType.CONTEXT, key);
        }

        if ("$req".equals(namespace)) {
            if ("uri".equals(key)) {
                return ReqConstant.URI;
            }
            if ("method".equals(key)) {
                return ReqConstant.METHOD;
            }
            if ("query".equals(key)) {
                return ReqConstant.QUERY;
            }
            if ("path".equals(key)) {
                return ReqConstant.PATH;
            }
        }

        return null;
    }

    public VarConfigSource buildConfigSource() {
        int s = 0;
        for (ConstPackage cp : packages) {
            if (cp != null) {
                cp.fixBaseIdx(s);
                s += cp.getLength();
            }
        }
        return new RouteMeta(packages, s);
    }


    public VarConfigSource buildConfigSource(int pathVarLength) {

        ConstPackage[] cps = packages;
        int s = pathVarLength;
        for (ConstPackage cp : cps) {
            if ((cp) != null && cp.getType() != VarType.PATH) {
                cp.fixBaseIdx(s);
                s += cp.getLength();
            }
        }
        return new RouteMeta(cps, s);
    }

    private static class RouteMeta implements VarConfigSource {
        private final ConstPackage[] packages;
        private final int varLength;

        private RouteMeta(ConstPackage[] packages, int varLength) {
            this.packages = packages;
            this.varLength = varLength;
        }

        @Override
        public int getVarIdx(VarType type, CharSequence key) {
            VarConst varConst;
            ConstPackage p;
            return (p = packages[type.ordinal()]) != null && (varConst = p.get(key)) != null ? varConst.getIdx() : -1;
        }

        @Override
        public void forEach(VarType type, Consumer<VarConst> consumer) {
            ConstPackage varConst;
            if ((varConst = packages[type.ordinal()]) != null) {
                varConst.forEach(consumer);
            }
        }

        @Override
        public int getVarLength() {
            return varLength;
        }

        @Override
        public int getVarLength(VarType type) {
            ConstPackage cp;
            if ((cp = packages[type.ordinal()]) != null) {
                return cp.getLength();
            }
            return 0;
        }
    }
}
