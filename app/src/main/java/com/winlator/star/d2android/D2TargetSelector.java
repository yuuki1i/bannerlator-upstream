package com.winlator.star.d2android;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Direction-aware target ranking over the native module's visible monster snapshot. */
public final class D2TargetSelector {
    private D2TargetSelector() {}

    public static List<D2ModUdpAdapter.Monster> rank(
            List<D2ModUdpAdapter.Monster> monsters, int originX, int originY, float aimX, float aimY) {
        List<Scored> scored = new ArrayList<>();
        double aimLength = Math.hypot(aimX, aimY);
        for (D2ModUdpAdapter.Monster monster : monsters) {
            if (monster == null || monster.hpPercent <= 0 || monster.id == 0xFFFFFFFFL) continue;
            double dx = monster.x - originX, dy = monster.y - originY;
            double distance = Math.hypot(dx, dy);
            if (distance < 1) continue;
            double alignment = aimLength < 0.08 ? 0 : (dx * aimX + dy * aimY) / (distance * aimLength);
            if (aimLength >= 0.08 && alignment < -0.1) continue;
            double score = distance / (0.35 + Math.max(0, alignment));
            scored.add(new Scored(monster, score));
        }
        scored.sort(Comparator.comparingDouble(s -> s.score));
        List<D2ModUdpAdapter.Monster> result = new ArrayList<>(scored.size());
        for (Scored s : scored) result.add(s.monster);
        return result;
    }

    private static final class Scored {
        final D2ModUdpAdapter.Monster monster;
        final double score;
        Scored(D2ModUdpAdapter.Monster monster, double score) {
            this.monster=monster; this.score=score;
        }
    }
}
