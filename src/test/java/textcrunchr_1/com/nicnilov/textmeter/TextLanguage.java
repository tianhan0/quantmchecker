package textcrunchr_1.com.nicnilov.textmeter;

import textcrunchr_1.com.nicnilov.textmeter.ngrams.Ngram;
import textcrunchr_1.com.nicnilov.textmeter.ngrams.NgramBuilder;
import textcrunchr_1.com.nicnilov.textmeter.ngrams.NgramType;
import textcrunchr_1.com.nicnilov.textmeter.ngrams.TextScore;
import textcrunchr_1.com.nicnilov.textmeter.ngrams.storage.LineFormatException;
import textcrunchr_1.com.nicnilov.textmeter.ngrams.storage.NgramStorageStrategy;
import plv.colorado.edu.quantmchecker.qual.Inv;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Created as part of textmeter project
 * by Nic Nilov on 25.10.13 at 23:19
 */
public class TextLanguage {

    private @Inv("+<self>=+TextLanguage41") EnumMap<NgramType, Ngram> ngrams = new @Inv("+<self>=+TextLanguage41") EnumMap(NgramType.class);

    private final String language;

    public TextLanguage(String language) {
        this.language = language;
    }

    protected Ngram getNgram(NgramType ngramType) {
        if (ngrams.containsKey(ngramType)) {
            return ngrams.get(ngramType);
        }
        throw new  NotInitializedException(String.format("Ngrams of type %s have not been loaded", ngramType));
    }

    public Ngram getNgram(NgramType ngramType, InputStream inputStream, NgramStorageStrategy ngramStorageStrategy, int sizeHint) throws IOException, LineFormatException {
        Ngram ngram = NgramBuilder.build(ngramType, inputStream, ngramStorageStrategy, sizeHint);
        TextLanguage41: ngrams.put(ngramType, ngram);
        return ngram;
    }

    public TextScore score(final String text) {
        TextScore textScore = new TextScore();
        Ngram ngram;
        TextLanguage48: for (Map.Entry<NgramType, Ngram> entry : ngrams.entrySet()) {
            if ((ngram = entry.getValue()) != null) {
                @Inv("ngrams+<self>=+TextLanguage50+TextLanguage51-TextLanguage48-TextLanguage48") EnumMap<NgramType, Ngram.ScoreStats> map1 = textScore.ngramScores;
                @Inv("ngrams+<self>=+TextLanguage50+TextLanguage51-TextLanguage48-TextLanguage48") EnumMap<NgramType, Ngram.ScoreStats> map2 = textScore.getNgramScores();
                TextLanguage50: map1.put(entry.getKey(), ngram.score(text));
                TextLanguage51: map2.put(entry.getKey(), ngram.score(text));
            }
        }
        return textScore;
    }

    public void releaseNgram(NgramType ngramType) {
        ngrams.remove(ngramType);
    }

    public void releaseAllNgrams() {
        ngrams.clear();
    }
}