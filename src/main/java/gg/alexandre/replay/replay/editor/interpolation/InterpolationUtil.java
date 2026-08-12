package gg.alexandre.replay.replay.editor.interpolation;

import org.joml.Vector3d;

public class InterpolationUtil {

    public static double linear(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double easeInOut(double a, double b, double t) {
        return a + (b - a) * (t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t);
    }

    public static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * ((2 * p1) +
                      (-p0 + p2) * t +
                      (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t +
                      (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
    }

    public static Vector3d catmullRomCentripetal(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, double t) {
        double alpha = 0.5; // 0.5 = Centripetal, 0 = Uniform, 1 = Chordal

        double t0 = 0.0;
        double t1 = t0 + Math.pow(p0.distance(p1), alpha);
        double t2 = t1 + Math.pow(p1.distance(p2), alpha);
        double t3 = t2 + Math.pow(p2.distance(p3), alpha);

        return catmullRomCentripetal(p0, p1, p2, p3, t0, t1, t2, t3, t);
    }

    public static Vector3d catmullRomCentripetal(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3,
                                                double t0, double t1, double t2, double t3, double t) {
        if (t1 == t2) return new Vector3d(p1);

        double t_ = t1 + t * (t2 - t1);

        Vector3d A1 = computeA(p0, p1, t0, t1, t_);
        Vector3d A2 = computeA(p1, p2, t1, t2, t_);
        Vector3d A3 = computeA(p2, p3, t2, t3, t_);

        Vector3d B1 = computeA(A1, A2, t0, t2, t_);
        Vector3d B2 = computeA(A2, A3, t1, t3, t_);

        return computeA(B1, B2, t1, t2, t_);
    }

    public static double catmullRomCentripetal(double p0, double p1, double p2, double p3,
                                               double t0, double t1, double t2, double t3, double t) {
        if (t1 == t2) return p1;

        double t_ = t1 + t * (t2 - t1);

        double A1 = computeScalarA(p0, p1, t0, t1, t_);
        double A2 = computeScalarA(p1, p2, t1, t2, t_);
        double A3 = computeScalarA(p2, p3, t2, t3, t_);

        double B1 = computeScalarA(A1, A2, t0, t2, t_);
        double B2 = computeScalarA(A2, A3, t1, t3, t_);

        return computeScalarA(B1, B2, t1, t2, t_);
    }

    private static Vector3d computeA(Vector3d pA, Vector3d pB, double tA, double tB, double t_) {
        if (tA == tB) return new Vector3d(pA);
        double w0 = (tB - t_) / (tB - tA);
        double w1 = (t_ - tA) / (tB - tA);
        return new Vector3d(
                pA.x * w0 + pB.x * w1,
                pA.y * w0 + pB.y * w1,
                pA.z * w0 + pB.z * w1
        );
    }

    private static double computeScalarA(double pA, double pB, double tA, double tB, double t_) {
        if (tA == tB) return pA;
        double w0 = (tB - t_) / (tB - tA);
        double w1 = (t_ - tA) / (tB - tA);
        return pA * w0 + pB * w1;
    }

}