package com.ketty.threadpool;

import java.util.concurrent.ConcurrentHashMap;

public class KtpRegistry {

    private static final ConcurrentHashMap<String, KettyThreadPool> EXECUTOR_MAP = new ConcurrentHashMap<>();

    public static void register(KettyThreadPool threadPool) {
        EXECUTOR_MAP.put(threadPool.getPoolName(), threadPool);
    }

    public static KettyThreadPool threadPool(String name) {
        return EXECUTOR_MAP.get(name);
    }

    public static void refresh(String name, int core, int max) {
        KettyThreadPool threadPool = EXECUTOR_MAP.get(name);
        if (threadPool != null) {
            threadPool.update(core, max);
        }
    }
}