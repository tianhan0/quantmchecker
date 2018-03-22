package com.nicnilov.textmeter.ngrams;

import java.util.EnumMap;
import java.util.Map;

/**
 * Created as part of textmeter project
 * by Nic Nilov on 27.10.13 at 23:03
 */
public class TextScore {

    private EnumMap<NgramType, Ngram.ScoreStats> ngramScores = new  EnumMap(NgramType.class);

    public EnumMap<NgramType, Ngram.ScoreStats> getNgramScores() {
        return ngramScores;
    }

    @Override
    public String toString() {
        StringBuilder sb = new  StringBuilder();
        for (Map.Entry<NgramType, Ngram.ScoreStats> entry : ngramScores.entrySet()) {
            if (entry.getValue() != null) {
                toStringHelper(entry, sb);
            }
        }
        return sb.toString();
    }

    private void toStringHelper(Map.Entry<NgramType, Ngram.ScoreStats> entry, StringBuilder sb) {
        sb.append(String.format("%s: %.5f (min: %.5f total: %.0f found: %.0f)", entry.getKey(), entry.getValue().getScore(), entry.getValue().getMinScore(), entry.getValue().getNgramsTotal(), entry.getValue().getNgramsFound()));
    }
}
