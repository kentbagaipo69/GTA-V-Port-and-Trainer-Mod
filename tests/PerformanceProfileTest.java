package dev.gtav.trainer;
import java.util.Arrays;
public final class PerformanceProfileTest {
    public static void main(String[] args) {
        long gb=1024L*1024*1024;
        assert PerformanceProfile.recommend(4*gb,false)==0;
        assert PerformanceProfile.recommend(6*gb,false)==1;
        assert PerformanceProfile.recommend(8*gb,false)==1;
        assert PerformanceProfile.recommend(12*gb,false)==2;
        assert PerformanceProfile.recommend(12*gb,true)==0;
        for(int tier=0;tier<3;tier++) {
            for(int[] display:new int[][]{{1080,2400},{2400,1080},{800,600},{1536,2048},{2208,1840},{720,1600}}) {
                String[] v=PerformanceProfile.values(tier,display[0],display[1]);
                assert Arrays.equals(v,PerformanceProfile.values(tier,display[1],display[0]));
                String[] resolution=v[0].split("x");int w=Integer.parseInt(resolution[0]),h=Integer.parseInt(resolution[1]);
                assert w%2==0&&h%2==0&&w>0&&h>0;
                assert w<=Math.max(display[0],display[1])&&h<=Math.min(display[0],display[1]);
                assert w<=new int[]{960,1280,1600}[tier];
                assert Math.abs(h-w*(double)Math.min(display[0],display[1])/Math.max(display[0],display[1]))<2;
            }
        }
        assert PerformanceProfile.values(0,2400,1080)[0].equals("960x432");
        assert PerformanceProfile.values(0,2400,1080)[1].equals("30");
        assert PerformanceProfile.values(2,2400,1080)[1].equals("60");
        try {PerformanceProfile.values(3,1080,2400);throw new AssertionError();} catch(IllegalArgumentException expected) {}
        try {PerformanceProfile.values(0,0,0);throw new AssertionError();} catch(IllegalArgumentException expected) {}
        System.out.println("PASS: RAM boundaries, low-RAM override, three tiers, six phone/tablet/foldable display shapes, rotation, even dimensions, no upscale, aspect ratio, FPS targets and invalid inputs.");
    }
}
