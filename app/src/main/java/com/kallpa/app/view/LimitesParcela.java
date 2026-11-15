package com.kallpa.app.view;

/** Validación geométrica local de vértices [longitud, latitud]. */
public final class LimitesParcela {
    private LimitesParcela() { }
    private static final double EPS = 1e-12;

    public static boolean validos(double[][] p) {
        if (p == null || p.length < 3 || p.length > 60) return false;
        double area = 0;
        for (int i=0;i<p.length;i++) {
            if (p[i] == null || p[i].length != 2 || !Double.isFinite(p[i][0]) || !Double.isFinite(p[i][1])
                    || Math.abs(p[i][0]) > 180 || Math.abs(p[i][1]) > 85) return false;
            for (int j=0;j<i;j++) {
                if (Math.abs(p[i][0]-p[j][0]) < 1e-7 && Math.abs(p[i][1]-p[j][1]) < 1e-7) return false;
            }
        }
        for (int i=0;i<p.length;i++) {
            int next=(i+1)%p.length;
            area += (p[i][0]-p[0][0])*(p[next][1]-p[0][1])
                    - (p[next][0]-p[0][0])*(p[i][1]-p[0][1]);
            for (int j=i+1;j<p.length;j++) {
                int end=(j+1)%p.length;
                if (next==j || end==i) continue;
                if (intersect(p[i],p[next],p[j],p[end])) return false;
            }
        }
        return Math.abs(area) > 1e-10;
    }
    private static double cross(double[] a,double[] b,double[] c) {
        return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
    }
    private static boolean on(double[] a,double[] b,double[] c) {
        return c[0]>=Math.min(a[0],b[0])-EPS && c[0]<=Math.max(a[0],b[0])+EPS
                && c[1]>=Math.min(a[1],b[1])-EPS && c[1]<=Math.max(a[1],b[1])+EPS;
    }
    private static boolean intersect(double[] a,double[] b,double[] c,double[] d) {
        double x=cross(a,b,c),y=cross(a,b,d),z=cross(c,d,a),w=cross(c,d,b);
        if (((x>EPS && y< -EPS)||(x< -EPS && y>EPS))
                && ((z>EPS && w< -EPS)||(z< -EPS && w>EPS))) return true;
        return (Math.abs(x)<=EPS && on(a,b,c)) || (Math.abs(y)<=EPS && on(a,b,d))
                || (Math.abs(z)<=EPS && on(c,d,a)) || (Math.abs(w)<=EPS && on(c,d,b));
    }
}
