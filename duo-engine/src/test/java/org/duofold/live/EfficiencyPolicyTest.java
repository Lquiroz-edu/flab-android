package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class EfficiencyPolicyTest {
 @Test public void motionHasPriorityWithoutCatchup(){assertEquals(8,EfficiencyPolicy.poll(true,true,false,0,false));assertEquals(32,EfficiencyPolicy.poll(true,false,false,0,false));assertEquals(1,EfficiencyPolicy.poll(true,true,false,100,false));assertEquals(0,EfficiencyPolicy.poll(true,false,false,100,true));}
 @Test public void sleepAndBatteryReduceWork(){assertEquals(500,EfficiencyPolicy.poll(false,true,false,0,true));assertEquals(64,EfficiencyPolicy.poll(true,false,true,0,false));assertEquals(15,EfficiencyPolicy.capture(120,false,false));assertEquals(8,EfficiencyPolicy.capture(60,false,true));assertEquals(30,EfficiencyPolicy.capture(120,true,true));assertEquals(60,EfficiencyPolicy.capture(-1,true,false));}
}
