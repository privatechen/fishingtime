package com.fishingtime.game.service;

import com.fishingtime.game.domain.OneStrokeScore;
import com.fishingtime.game.dto.OneStrokeScoreSubmitDTO;

public interface OneStrokeScoreService {
    OneStrokeScore getMyBest(Long userId);
    void submitScore(Long userId, OneStrokeScoreSubmitDTO dto);
}
