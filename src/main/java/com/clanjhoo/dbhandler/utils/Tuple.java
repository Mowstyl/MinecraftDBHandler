package com.clanjhoo.dbhandler.utils;

/**
 * A tuple containing two items of any kind
 * @param <F> the type of the first item in the tuple
 * @param <S> the type of the second item in the tuple
 */
public class Tuple<F, S> {
    private final F first;
    private final S second;

    /**
     * Creates a new instance of a Pair with the given values stored in it
     * @param first the first element in the tuple
     * @param second the second element in the tuple
     */
    public Tuple(F first, S second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first element in the tuple
     * @return the first element
     */
    public F getFirst() {
        return first;
    }

    /**
     * Returns the second element in the tuple
     * @return the second element
     */
    public S getSecond() {
        return second;
    }
}
