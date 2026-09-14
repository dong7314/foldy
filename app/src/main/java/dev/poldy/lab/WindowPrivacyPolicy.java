package dev.poldy.lab;

/** Values verified against this device's InputConfig; no window titles are retained. */
final class WindowPrivacyPolicy {
    static boolean visible(int display,int input,float alpha,boolean empty) {
        return display>=0&&display<=1&&(input&0x2)==0&&(input&0x10000)==0&&alpha>0&&!empty;
    }
    static boolean protectedContent(int flags,int input) {
        return (flags&0x2000)!=0||(input&0x40000)!=0;
    }
    static boolean application(int type){return type>=1&&type<=1999;}
    static boolean ownRenderSurface(int uid,int owner,int type){return uid==owner&&type==0;}
    static boolean systemPowerFade(int uid,int type,int flags,int input,String name){
        return uid==1000&&type==0&&flags==0&&(input&1)!=0&&name!=null
            &&name.matches("ColorFade(?: BLAST_d[01]|_d[01]_child-surface)(?:#[0-9]+)?");
    }
}
