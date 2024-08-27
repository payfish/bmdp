package com.hmdp.utils;


public interface ILock {

    boolean tryLock(long timeoutSeconds);

    void unlock();
}
