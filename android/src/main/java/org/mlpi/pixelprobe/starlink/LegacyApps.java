package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.content.pm.PackageManager;
import java.util.*;

/** Detect known older listeners; normal apps cannot stop or migrate another app's sandbox. */
final class LegacyApps {
    static final class Copy {
        final String packageName,title;final boolean enabled;
        Copy(String packageName,String title,boolean enabled){this.packageName=packageName;this.title=title;this.enabled=enabled;}
    }
    static List<Copy> installed(Context context) {
        String[] packages={"org.mlpi.pixelprobe.dev","org.subarubridge.legacyresearch","com.subaru.global.infotainment.gen2"};
        String[] titles={"Earlier SubaruBridge Lab","Earlier private STARLINK research","Original STARLINK"};
        List<Copy> result=new ArrayList<>();
        for(int i=0;i<packages.length;i++)try {
            var info=context.getPackageManager().getApplicationInfo(packages[i],0);
            result.add(new Copy(packages[i],titles[i],info.enabled));
        }catch(PackageManager.NameNotFoundException ignored){}
        return result;
    }
    static int enabledCount(Context context){int count=0;for(Copy copy:installed(context))if(copy.enabled)count++;return count;}
    private LegacyApps(){}
}
