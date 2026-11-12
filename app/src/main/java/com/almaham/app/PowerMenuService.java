package com.almaham.app;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
public class PowerMenuService extends AccessibilityService {
 private static PowerMenuService instance;
 @Override protected void onServiceConnected(){instance=this;}
 static boolean showPowerMenu(){return instance!=null&&instance.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){instance=null;super.onDestroy();}
}
