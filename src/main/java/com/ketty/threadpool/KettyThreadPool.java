package com.ketty.threadpool;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
public class KettyThreadPool {
    private final String name;

    private BlockingQueue<Runnable> taskQueue;

    private HashSet<Worker> workers = new HashSet<Worker>();

    private int coreSize;
    private int maxCoreSize;

    // 一个线程如果没有人用足够多的时间就直接撤了
    private long timeout;

    private TimeUnit timeUnit;

    private ReJectPolicy<Runnable> reJectPolicy;
    private boolean exist = false;

    public KettyThreadPool(String name, long timeout, TimeUnit timeUnit, int queueCapacity, ReJectPolicy reJectPolicy) {
        this.name = name;
        if(exist) return;
        exist = true;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.coreSize = Runtime.getRuntime().availableProcessors();
        this.maxCoreSize = this.coreSize + 1;
        this.taskQueue = new BlockingQueue<>(queueCapacity);
        this.reJectPolicy = reJectPolicy;
    }
    public KettyThreadPool(String name, long timeout, TimeUnit timeUnit, int coreSize, int maxCoreSize, int queueCapacity, ReJectPolicy reJectPolicy) {
        this.name = name;
        if(exist) return;
        exist = true;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.coreSize = coreSize;
        this.maxCoreSize = maxCoreSize;
        this.taskQueue = new BlockingQueue<>(queueCapacity);
        this.reJectPolicy = reJectPolicy;
    }

    public String getPoolName(){
        return name;
    }
    public void execute(Runnable task){
        // 当任务数量没有超过CoreSize 直接创建一个去执行
        // 如果超过了，加入任务队列
        boolean canStart = false;
        if (StartThread(task, coreSize)) return;

        if (!taskQueue.isFull()) {
            if(taskQueue.put(task)) return;
        }
        if (StartThread(task, maxCoreSize)) return;
        reJectPolicy.reject(taskQueue, task);
    }

    private boolean StartThread(Runnable task, int size) {
        boolean canStart = false;
        if(workers.size() < size){
            Worker worker = new Worker(task);
            synchronized (KettyThreadPool.class){
                if(workers.size() < size) {
                    workers.add(worker);
                    canStart = true;
                }
            }
            if(canStart) {
                // 这里就是启动一个线程了。如果线程数量不够的话
                worker.start();
                return true;
            }
        }
        return false;
    }

    public void update(int core, int max) {
        this.coreSize = core;
        this.maxCoreSize = max;
    }

    class Worker extends Thread{
        private Runnable task;

        public Worker(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            while(true){
                if(task != null){
                    try {
                        task.run();
                    }catch (Exception e){
                        e.printStackTrace();
                    }finally {
                        task = null;
                    }
                }else{
                    task = taskQueue.pool(timeout, timeUnit);
                    if(task == null) break;
                }
            }
            synchronized (KettyThreadPool.class){
                workers.remove(this);
            }

        }
    }
}
class ReJectPolicy<T>{
    void reject(BlockingQueue<T>queue, T task) {
        queue.put(task);
    };
}
class BlockingQueue<T>{
    private Deque<T> queue = new ArrayDeque<>();

    private ReentrantLock lock= new ReentrantLock();

    private Condition fullWaitSet = lock.newCondition();
    private Condition emptyWaitSet = lock.newCondition();

    private int capacity;
    public int getMaxCapacity() { return capacity; }
    public int getCapacity() {return queue.size(); }
    public boolean isFull() { return capacity <= queue.size(); }
    public BlockingQueue(int capacity){
        this.capacity = capacity;
    }

    /**
     * 有超时等待的拉取任务
     * @param timeout
     * @param unit
     * @return
     */
    public T pool(long timeout, TimeUnit unit){
        lock.lock();
        try {
            long l = unit.toNanos(timeout);
            while(queue.isEmpty()){
                try {
                    if(l <= 0) return null;
                    l = emptyWaitSet.awaitNanos(l);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            fullWaitSet.signal();
            return queue.removeFirst();
        }finally {
            lock.unlock();
        }
    }

    public T take(){
        lock.lock();
        try {
            while(queue.isEmpty()){
                try {
                    emptyWaitSet.await();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            fullWaitSet.signal();
            return queue.removeFirst();
        }finally {
            lock.unlock();
        }
    }
    public boolean put(T t){
        lock.lock();
        try{
            if(queue.size() < capacity){
                queue.addLast(t);
                emptyWaitSet.signal();
                return true;
            }
        }finally {
            lock.unlock();
        }
        return false;
    }

//    public void put(T t){
//        lock.lock();
//        try{
//            while(queue.size() >= capacity){
//                try {
//                    fullWaitSet.await();
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//            queue.addLast(t);
//            emptyWaitSet.signal();
//        }finally {
//            lock.unlock();
//        }
//    }

    public boolean putWithTimeout(T t, long timeout, TimeUnit unit){
        lock.lock();
        try{
            long l = unit.toNanos(timeout);
            while(queue.size() >= capacity){
                try {
                    if(l <= 0) return false;
                    l = fullWaitSet.awaitNanos(l);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("添加进队列了");
            queue.addLast(t);
            emptyWaitSet.signal();
            return true;
        }finally {
            lock.unlock();
        }
    }
}
