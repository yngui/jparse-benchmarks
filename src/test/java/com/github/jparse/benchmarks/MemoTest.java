/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Igor Konev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.github.jparse.benchmarks;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.github.jparse.FluentParser;
import com.github.jparse.Function;
import com.github.jparse.ParseResult;
import com.github.jparse.Parser;
import com.github.jparse.Sequence;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static com.github.jparse.CharParsers.literal;
import static com.github.jparse.Sequences.fromCharSequence;
import static com.github.jparse.StatefulParsers.memo;
import static com.github.jparse.StatefulSequences.stateful;
import static java.util.Objects.requireNonNull;

public class MemoTest {

    @Rule
    public TestRule benchmarkRun = new BenchmarkRule();

    private static final FluentParser<Character, Object> rrOrig;
    private static final FluentParser<Character, Object> rrMod;
    private static final FluentParser<Character, Object> lrMod;
    private static final Sequence<Character> sequence;

    static {
        FluentParser<Character, String> x = literal("x");

        FluentParser<Character, Object> rrOrigRef = new FluentParser<Character, Object>() {
            @Override
            public ParseResult<Character, ?> parse(Sequence<Character> sequence) {
                return rrOrig.parse(sequence);
            }
        };
        rrOrig = new MemoParser<>(x.then(rrOrigRef).map(cast(Object.class)).orelse(x)).phrase();

        FluentParser<Character, Object> rrModRef = new FluentParser<Character, Object>() {
            @Override
            public ParseResult<Character, ?> parse(Sequence<Character> sequence) {
                return rrMod.parse(sequence);
            }
        };
        rrMod = memo(x.then(rrModRef).map(cast(Object.class)).orelse(x)).phrase();

        FluentParser<Character, Object> lrModRef = new FluentParser<Character, Object>() {
            @Override
            public ParseResult<Character, ?> parse(Sequence<Character> sequence) {
                return lrMod.parse(sequence);
            }
        };
        lrMod = memo(lrModRef.then(x).map(cast(Object.class)).orelse(x)).phrase();

        StringBuilder sb = new StringBuilder(100000);
        for (int i = 0; i < 100000; i++) {
            sb.append('x');
        }
        sequence = fromCharSequence(sb.toString());
    }

    @BenchmarkOptions(benchmarkRounds = 100, warmupRounds = 50)
    @Test
    public void rrOrig() {
        rrOrig.parse(new MemoSequence<>(sequence));
    }

    @BenchmarkOptions(benchmarkRounds = 100, warmupRounds = 50)
    @Test
    public void rrMod() {
        rrMod.parse(stateful(sequence));
    }

    @BenchmarkOptions(benchmarkRounds = 100, warmupRounds = 50)
    @Test
    public void lrMod() {
        lrMod.parse(stateful(sequence));
    }

    private static <T, U> Function<? super T, ? extends U> cast(final Class<U> cls) {
        return new Function<T, U>() {
            @Override
            public U apply(T arg) {
                return cls.cast(arg);
            }
        };
    }

    private static final class MemoParser<T, U> extends FluentParser<T, U> {

        private final Parser<T, ? extends U> parser;

        MemoParser(Parser<T, ? extends U> parser) {
            this.parser = parser;
        }

        @Override
        public ParseResult<T, ? extends U> parse(Sequence<T> sequence) {
            Map<Sequence<T>, ParseResult<T, ?>> results = ((MemoSequence<T>) sequence).results;
            @SuppressWarnings("unchecked")
            ParseResult<T, ? extends U> result = (ParseResult<T, ? extends U>) results.get(sequence);
            if (result == null) {
                result = parser.parse(sequence);
                results.put(sequence, result);
            }
            return result;
        }
    }

    private static final class MemoSequence<T> implements Sequence<T> {

        private final Sequence<T> sequence;
        final Map<Sequence<T>, ParseResult<T, ?>> results;

        MemoSequence(Sequence<T> sequence) {
            this(requireNonNull(sequence), new HashMap<Sequence<T>, ParseResult<T, ?>>());
        }

        private MemoSequence(Sequence<T> sequence, Map<Sequence<T>, ParseResult<T, ?>> results) {
            this.sequence = sequence;
            this.results = results;
        }

        @Override
        public int length() {
            return sequence.length();
        }

        @Override
        public T at(int index) {
            return sequence.at(index);
        }

        @Override
        public MemoSequence<T> subSequence(int start) {
            if (start == 0) {
                return this;
            }
            return new MemoSequence<>(sequence.subSequence(start), results);
        }

        @Override
        public MemoSequence<T> subSequence(int start, int end) {
            if (start == 0 && end == sequence.length()) {
                return this;
            }
            return new MemoSequence<>(sequence.subSequence(start, end), results);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof MemoSequence)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            MemoSequence<T> other = (MemoSequence<T>) obj;
            return sequence.equals(other.sequence);
        }

        @Override
        public int hashCode() {
            return sequence.hashCode();
        }

        @Override
        public String toString() {
            return sequence.toString();
        }
    }
}
