package dev.poldy.lab;

import android.graphics.Bitmap;
import android.graphics.ParcelableColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Parcel;
import android.os.Parcelable;

/** Transfers a GPU buffer by handle. The sender closes its reference after writing the reply. */
public final class CapturedFrame implements Parcelable, AutoCloseable {
    HardwareBuffer buffer;
    ParcelableColorSpace color;
    long captureMillis;
    int baseState=-1;
    String error;
    String owner;
    long generation=-1;
    boolean nativeReady,inner;
    CapturedFrame() {}
    private CapturedFrame(Parcel in) {
        buffer=in.readTypedObject(HardwareBuffer.CREATOR);
        color=in.readTypedObject(ParcelableColorSpace.CREATOR);
        captureMillis=in.readLong();baseState=in.readInt();error=in.readString();
        owner=in.readString();generation=in.readLong();nativeReady=in.readBoolean();inner=in.readBoolean();
    }
    Bitmap takeBitmap() {
        try {return buffer==null?null:Bitmap.wrapHardwareBuffer(buffer,color.getColorSpace());}
        finally {close();}
    }
    @Override public void close() {if(buffer!=null){buffer.close();buffer=null;}}
    @Override public int describeContents(){return CONTENTS_FILE_DESCRIPTOR;}
    @Override public void writeToParcel(Parcel out,int flags) {
        out.writeTypedObject(buffer,flags);out.writeTypedObject(color,flags);
        out.writeLong(captureMillis);out.writeInt(baseState);out.writeString(error);
        out.writeString(owner);out.writeLong(generation);out.writeBoolean(nativeReady);out.writeBoolean(inner);
        if((flags & PARCELABLE_WRITE_RETURN_VALUE)!=0)close();
    }
    public static final Creator<CapturedFrame> CREATOR=new Creator<>() {
        @Override public CapturedFrame createFromParcel(Parcel in){return new CapturedFrame(in);}
        @Override public CapturedFrame[] newArray(int size){return new CapturedFrame[size];}
    };
}
