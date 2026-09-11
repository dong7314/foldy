package dev.poldy.lab;

/** Relative primary-IMU orientation. It is not the viewer's eye pose or a second hinge sensor. */
final class FoldAttitude {
    private float w=1,x,y,z,pitch,yaw,roll;
    private long previous,rendered;
    private boolean active;
    void begin(){if(active)return;active=true;w=1;x=y=z=0;}
    void rest(){active=false;}
    void gyro(long at,float rx,float ry,float rz){
        if(!Float.isFinite(rx)||!Float.isFinite(ry)||!Float.isFinite(rz)||at<=previous)return;
        float dt=(at-previous)*1e-9f;long old=previous;previous=at;
        if(!active||old==0||dt>.12f)return;
        float speed=(float)Math.sqrt(rx*rx+ry*ry+rz*rz);
        if(speed<.008f)return;
        float half=speed*dt*.5f,s=(float)Math.sin(half)/speed;
        float a=(float)Math.cos(half),b=rx*s,c=ry*s,d=rz*s;
        float nw=w*a-x*b-y*c-z*d,nx=w*b+x*a+y*d-z*c;
        float ny=w*c-x*d+y*a+z*b,nz=w*d+x*c-y*b+z*a;
        float norm=(float)Math.sqrt(nw*nw+nx*nx+ny*ny+nz*nz);
        w=nw/norm;x=nx/norm;y=ny/norm;z=nz/norm;
    }
    void step(long now){
        float dt=rendered==0?.016f:FoldOptics.clamp((now-rendered)/1000f,0,.06f);rendered=now;
        float blend=1-(float)Math.exp(-dt/.09f);
        float tx=(float)Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));
        float ty=(float)Math.asin(FoldOptics.clamp(2*(w*y-z*x),-1,1));
        float tz=(float)Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));
        pitch+=(FoldOptics.clamp(tx,-.65f,.65f)-pitch)*blend;
        yaw+=(FoldOptics.clamp(ty,-.65f,.65f)-yaw)*blend;
        roll+=(FoldOptics.clamp(tz,-.65f,.65f)-roll)*blend;
    }
    float pitch(){return pitch;}float yaw(){return yaw;}float roll(){return roll;}
}
