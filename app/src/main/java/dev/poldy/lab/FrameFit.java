package dev.poldy.lab;

/** Contains every source pixel without stretching or cropping either panel. */
final class FrameFit {
    final float scale,x,y;
    FrameFit(int sourceWidth,int sourceHeight,int targetWidth,int targetHeight){
        scale=Math.min(targetWidth/(float)sourceWidth,targetHeight/(float)sourceHeight);
        x=(targetWidth-sourceWidth*scale)/2;y=(targetHeight-sourceHeight*scale)/2;
    }
}
