import Foundation
import AVFoundation
import CoreVideo
let asset=AVURLAsset(url:URL(fileURLWithPath:CommandLine.arguments[1]))
let reader=try AVAssetReader(asset:asset)
let track=asset.tracks(withMediaType:.video).first!
let output=AVAssetReaderTrackOutput(track:track,outputSettings:[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_32BGRA])
reader.add(output);reader.startReading()
var rows:[[String:Any]]=[]
while let sample=output.copyNextSampleBuffer() {
 guard let pixel=CMSampleBufferGetImageBuffer(sample) else{continue}
 CVPixelBufferLockBaseAddress(pixel,.readOnly)
 let width=CVPixelBufferGetWidth(pixel),height=CVPixelBufferGetHeight(pixel),stride=CVPixelBufferGetBytesPerRow(pixel)
 let p=CVPixelBufferGetBaseAddress(pixel)!.assumingMemoryBound(to:UInt8.self)
 var sum=0.0,dark=0,count=0,brightest=0.0
 for y in Swift.stride(from:height/10,to:height*9/10,by:max(1,height/100)) {
  for x in Swift.stride(from:width/10,to:width*9/10,by:max(1,width/100)) {
   let i=y*stride+x*4,luma=0.2126*Double(p[i+2])+0.7152*Double(p[i+1])+0.0722*Double(p[i])
   sum+=luma;brightest=max(brightest,luma);if luma<10{dark+=1};count+=1
  }
 }
 rows.append(["seconds":CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample)),"mean":sum/Double(count),"dark_fraction":Double(dark)/Double(count),"max":brightest])
 CVPixelBufferUnlockBaseAddress(pixel,.readOnly)
}
try JSONSerialization.data(withJSONObject:rows,options:[.sortedKeys]).write(to:URL(fileURLWithPath:CommandLine.arguments[2]))
print("frames=\(rows.count), status=\(reader.status.rawValue), error=\(String(describing:reader.error))")
