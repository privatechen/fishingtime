package com.fishingtime.game.leaderboard;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class GameRankingConfig {
    private static final class Spec {
        private final String direction; private final boolean useSecondary;
        Spec(String direction, boolean useSecondary) { this.direction=direction; this.useSecondary=useSecondary; }
    }
    private static final Map<String,Spec> SPECS=new HashMap<>();
    static {
        SPECS.put("2048",new Spec("desc",false)); SPECS.put("color-focus",new Spec("desc",false));
        SPECS.put("direction-trap",new Spec("desc",false)); SPECS.put("color-hunter",new Spec("asc",false));
        SPECS.put("fish-breakout",new Spec("desc",true)); SPECS.put("extreme-fishing",new Spec("desc",false));
        SPECS.put("detail",new Spec("desc",true)); SPECS.put("dont-fill",new Spec("desc",true));
        SPECS.put("stack-tower",new Spec("desc",true)); SPECS.put("one-stroke",new Spec("desc",false));
        // Number puzzle primary score is elapsed milliseconds: lower is better.
        SPECS.put("number-puzzle",new Spec("asc",false));
    }
    private static final Spec DEFAULT=new Spec("desc",false);
    public boolean isKnown(String gameCode){return SPECS.containsKey(gameCode);}
    public String direction(String gameCode){return SPECS.getOrDefault(gameCode,DEFAULT).direction;}
    public boolean useSecondary(String gameCode){return SPECS.getOrDefault(gameCode,DEFAULT).useSecondary;}
}
