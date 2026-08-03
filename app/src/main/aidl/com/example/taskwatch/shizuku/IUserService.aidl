package com.example.taskwatch.shizuku;

interface IUserService {
    void destroy() = 16777114;
    String listRunningProcesses() = 1;
    boolean forceStopPackage(String packageName) = 2;
}
