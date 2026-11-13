package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DrawGuessWordRepository {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final List<Word> words = DrawGuessWords.all();

    public List<Word> findAll() { return Collections.unmodifiableList(words); }

    public List<Word> drawOptions() { return drawOptions(Set.of()); }

    public List<Word> drawOptions(Set<Integer> excludedIds) {
        List<Word> candidates = words.stream()
                .filter(word -> !excludedIds.contains(word.id()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.size() < 3) {
            candidates = new ArrayList<>(words);
        }
        Collections.shuffle(candidates, RANDOM);
        return List.copyOf(candidates.subList(0, 3));
    }

    public Word findById(int id) {
        return words.stream().filter(word -> word.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
    }

    public int size() { return words.size(); }
    public record Word(int id, String category, String answer) {}
}
