package org.duofold.live;
/** No overlapping polls; lower cadence while still and bounded capture cost. */
final class EfficiencyPolicy {
 static long poll(boolean interactive,boolean moving,boolean saving,long elapsed,boolean urgent){
  if(urgent&&interactive)return 0;
  long budget=!interactive?500:moving?(saving?16:8):(saving?64:32);
  return Math.max(1,budget-Math.max(0,elapsed));
 }
 static int capture(int requested,boolean moving,boolean saving){
  int fps=RenderQuality.fps(requested);
  return Math.min(fps,saving?(moving?30:8):(moving?fps:15));
 }
}
