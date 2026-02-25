package com.ketty.threadpool;

import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        KettyThreadPool threadPool = new KettyThreadPool("123", 10, TimeUnit.SECONDS, 1, 2, 5, new ReJectPolicy() {
            @Override
            void reject(BlockingQueue queue, Object task) {
                System.out.println("man le");
            }
        });
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            threadPool.execute(()->{
                System.out.print(finalI);
                System.out.println("开始了");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.print(finalI);
                System.out.println("结束了");
            });
        }
    }
}
