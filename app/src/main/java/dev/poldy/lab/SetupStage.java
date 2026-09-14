package dev.poldy.lab;

/** UI progress follows confirmed prerequisites, never an elapsed animation timer. */
enum SetupStage {
    EXPERIENCE, CONNECT, READY, STARTING, PAUSED, ACTIVE;
    static SetupStage resolve(boolean experienced,boolean connected,boolean running,boolean prepared){
        if(running)return !connected?CONNECT:prepared?ACTIVE:STARTING;
        if(!experienced)return EXPERIENCE;
        return connected?READY:CONNECT;
    }
    static SetupStage resolve(boolean experienced,boolean connected,boolean running,boolean prepared,boolean suspended){
        if(running&&connected&&suspended)return PAUSED;
        return resolve(experienced,connected,running,prepared);
    }
    int step(){return this==EXPERIENCE?0:this==CONNECT?1:2;}
}
