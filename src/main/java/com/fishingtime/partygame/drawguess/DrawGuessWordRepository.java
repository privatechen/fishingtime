package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small in-memory word bank for the first release. The answer is never
 * included in public room snapshots; the message layer will send it only
 * to the selected artist.
 */
@Component
public class DrawGuessWordRepository {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final List<Word> words = DrawGuessWords.all();

    public List<Word> findAll() {
        return Collections.unmodifiableList(words);
    }

    public List<Word> drawOptions() {
        if (words.size() < 3) throw new IllegalStateException("题库至少需要三个题目");
        List<Word> copy = new ArrayList<>(words);
        Collections.shuffle(copy, RANDOM);
        return List.copyOf(copy.subList(0, 3));
    }

    public int size() {
        return words.size();
    }

    public record Word(int id, String category, String answer) {}
}
